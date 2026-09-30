"""KAMIS 1kg 도매가격을 MealFit 식재료에 보수적으로 매핑하고 이력을 적재한다.

기본 실행은 변경 없는 사전 점검이다. 실제 반영은 ``--apply``를 지정한다.
이미 존재하는 시계열과 (시계열, 일자) 가격은 다시 만들지 않는다.
"""

from __future__ import annotations

import argparse
import calendar
import json
import os
import re
import time
from dataclasses import dataclass
from datetime import date, datetime, timedelta
from decimal import Decimal, InvalidOperation
from pathlib import Path

import psycopg
import requests


API_URL = "https://www.kamis.or.kr/service/price/xml.do"
REGIONS = {
    "서울": ("1101", "가락도매"),
    "부산": ("2100", "엄궁도매"),
    "대전": ("2501", "오정도매"),
}


@dataclass(frozen=True)
class Mapping:
    ingredient_code: str
    category_code: str
    item_code: str
    kind_code: str
    rank_code: str
    variety: str
    grade: str


# 개수/마리/L 단위와 가공품은 제외했다. 같은 KAMIS 대표가격을 쓰는 색상별
# 파프리카처럼 품목 수준에서만 구분되는 경우는 명시적인 대체가격으로 연결한다.
MAPPINGS = (
    Mapping("F00016", "100", "111", "01", "04", "20kg(1kg)", "상품"),
    Mapping("F00281", "100", "115", "01", "04", "찰현미(1kg)", "상품"),
    Mapping("F00453", "100", "141", "01", "04", "흰 콩(국산)(1kg)", "상품"),
    Mapping("F00482", "100", "142", "00", "04", "붉은 팥(국산)(1kg)", "상품"),
    Mapping("F00329", "100", "151", "00", "04", "밤(1kg)", "상품"),
    Mapping("F00305", "100", "152", "01", "04", "수미(노지)(1kg)", "상품"),
    Mapping("F00815", "200", "211", "02", "04", "여름(고랭지)(1kg)", "상품"),
    Mapping("F00916", "200", "214", "01", "04", "적(1kg)", "상품"),
    Mapping("F00917", "200", "214", "02", "04", "청(1kg)", "상품"),
    Mapping("F00772", "200", "231", "02", "04", "고랭지(1kg)", "상품"),
    Mapping("F00739", "200", "258", "03", "04", "깐마늘(대서)(1kg)", "상품"),
    Mapping("F00792", "200", "252", "00", "04", "미나리(1kg)", "상품"),
    Mapping("F00868", "200", "254", "00", "04", "부추(1kg)", "상품"),
    Mapping("F01153", "200", "256", "00", "04", "파프리카(1kg)", "상품"),
    Mapping("F01355", "200", "257", "00", "04", "멜론(1kg)", "상품"),
    Mapping("F01132", "200", "422", "02", "04", "대추방울토마토(1kg)", "상품"),
    Mapping("F03487", "400", "411", "07", "04", "홍로(1kg)", "상품"),
    Mapping("F01299", "400", "415", "02", "14", "시설(1kg)", "M과"),
    Mapping("F03175", "500", "4301", "21", "01", "안심", "1++등급"),
    Mapping("F01700", "500", "4301", "22", "01", "등심", "1++등급"),
    Mapping("F03466", "500", "4301", "36", "01", "설도", "1++등급"),
    Mapping("F03143", "500", "4301", "40", "01", "양지", "1++등급"),
    Mapping("F03249", "500", "4301", "50", "01", "갈비", "1++등급"),
    Mapping("F01742", "500", "4301", "21", "03", "안심", "1등급"),
    Mapping("F01693", "500", "4301", "22", "03", "등심", "1등급"),
    Mapping("F03335", "500", "4301", "36", "03", "설도", "1등급"),
    Mapping("F03337", "500", "4301", "40", "03", "양지", "1등급"),
    Mapping("F03181", "500", "4301", "50", "03", "갈비", "1등급"),
    Mapping("F03251", "500", "4304", "25", "00", "앞다리", "앞다리"),
    Mapping("F03125", "500", "4304", "27", "00", "삼겹살", "삼겹살"),
    Mapping("F01577", "500", "9901", "99", "00", "육계(kg)", "육계(kg)"),
    Mapping("F02154", "600", "616", "02", "21", "냉동(1kg)", "中"),
    Mapping("F02411", "600", "653", "00", "05", "냉장(1kg)", "중품"),
    Mapping("F02438", "600", "658", "02", "04", "안깐홍합(냉장)(1kg)", "상품"),
)


def read_env(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    return values


def transaction_pool_url(jdbc_url: str) -> str:
    url = jdbc_url.removeprefix("jdbc:")
    return re.sub(r":5432(?=/)", ":6543", url)


def chunks(start: date, end: date):
    cursor = start
    while cursor <= end:
        # KAMIS는 윤년이 걸친 365일 차이(포함 366일)를 500으로 거부할 수 있다.
        chunk_end = min(cursor + timedelta(days=180), end)
        yield cursor, chunk_end
        cursor = chunk_end + timedelta(days=1)


def parse_price(raw: object) -> Decimal | None:
    text = str(raw or "").replace(",", "").strip()
    if not text or text in {"-", "--"}:
        return None
    try:
        price = Decimal(text)
    except InvalidOperation:
        return None
    return price if price >= 0 else None


def parse_price_date(year: object, regday: object) -> date | None:
    year_text = str(year or "").strip()
    day_text = str(regday or "").strip()
    for value, pattern in (
        (f"{year_text}-{day_text}", "%Y-%m/%d"),
        (day_text, "%Y-%m-%d"),
        (day_text, "%Y.%m.%d"),
    ):
        try:
            return datetime.strptime(value, pattern).date()
        except ValueError:
            pass
    return None


def fetch_rows(
    session: requests.Session,
    credentials: dict[str, str],
    mapping: Mapping,
    country_code: str,
    start: date,
    end: date,
) -> list[dict]:
    params = {
        "action": "periodWholesaleProductList",
        "p_startday": start.isoformat(),
        "p_endday": end.isoformat(),
        "p_itemcategorycode": mapping.category_code,
        "p_itemcode": mapping.item_code,
        "p_kindcode": mapping.kind_code,
        "p_productrankcode": mapping.rank_code,
        "p_countrycode": country_code,
        "p_convert_kg_yn": "Y",
        "p_cert_key": credentials["KAMIS_API_KEY"],
        "p_cert_id": credentials["KAMIS_CERT_ID"],
        "p_returntype": "json",
    }
    response = None
    for attempt in range(3):
        response = session.get(API_URL, params=params, timeout=45)
        if response.status_code < 500:
            break
        time.sleep(1.5 * (attempt + 1))
    if response is None or response.status_code >= 400:
        status = "응답 없음" if response is None else str(response.status_code)
        raise RuntimeError(
            f"KAMIS HTTP {status}: {mapping.item_code}/{mapping.kind_code}, {start}~{end}"
        )
    payload = response.json()
    data = payload.get("data") or {}
    code = data.get("error_code")
    if code == "001":
        return []
    if code != "000":
        raise RuntimeError(f"KAMIS 오류 {code}: {mapping.item_code}/{mapping.kind_code}")
    return data.get("item") or []


def find_or_create_series(cur, ingredient_id: int, mapping: Mapping, region: str, market: str, apply: bool):
    cur.execute(
        """
        SELECT series_id, ingredient_id
        FROM mealfit.price_series
        WHERE source_name = 'KAMIS'
          AND source_category_code = %s AND source_item_code = %s
          AND source_kind_code = %s AND source_rank_code = %s
          AND price_type = 'WHOLESALE' AND market = %s AND region = %s
        ORDER BY series_id LIMIT 1
        """,
        (mapping.category_code, mapping.item_code, mapping.kind_code,
         mapping.rank_code, market, region),
    )
    found = cur.fetchone()
    if found:
        series_id, owner_ingredient_id = found
        created = False
    else:
        if not apply:
            return None, True
        cur.execute(
            """
            INSERT INTO mealfit.price_series
                (ingredient_id, source_name, source_category_code, source_item_code,
                 source_kind_code, source_rank_code, variety, grade, price_type,
                 market, region, original_unit, unit_quantity, is_cost_basis)
            VALUES (%s, 'KAMIS', %s, %s, %s, %s, %s, %s, 'WHOLESALE',
                    %s, %s, 'kg', 1, false)
            RETURNING series_id
            """,
            (ingredient_id, mapping.category_code, mapping.item_code,
             mapping.kind_code, mapping.rank_code, mapping.variety, mapping.grade,
             market, region),
        )
        series_id = cur.fetchone()[0]
        owner_ingredient_id = ingredient_id
        created = True

    if apply:
        exact = owner_ingredient_id == ingredient_id
        cur.execute(
            """
            INSERT INTO mealfit.ingredient_price_mapping
                (ingredient_id, series_id, mapping_type, conversion_factor,
                 confidence_score, priority, review_status, is_active)
            VALUES (%s, %s, %s, 1, %s, %s, 'APPROVED', TRUE)
            ON CONFLICT (ingredient_id, series_id) DO UPDATE SET
                mapping_type = EXCLUDED.mapping_type,
                confidence_score = EXCLUDED.confidence_score,
                priority = EXCLUDED.priority,
                review_status = 'APPROVED', is_active = TRUE,
                updated_at = CURRENT_TIMESTAMP
            """,
            (ingredient_id, series_id, "EXACT" if exact else "VARIETY",
             Decimal("1") if exact else Decimal("0.85"), 0 if exact else 20),
        )
    return series_id, created


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--start", type=date.fromisoformat, default=date(2023, 1, 2))
    parser.add_argument("--end", type=date.fromisoformat, default=date(2026, 9, 22))
    parser.add_argument("--resume-after", help="이 식재료 코드 다음부터 재개")
    args = parser.parse_args()
    if args.end < args.start:
        parser.error("--end는 --start보다 빠를 수 없습니다.")

    env = read_env(Path(__file__).resolve().parents[1] / ".env")
    required = ("DB_URL", "DB_USERNAME", "DB_PASSWORD", "KAMIS_API_KEY", "KAMIS_CERT_ID")
    missing = [key for key in required if not env.get(key)]
    if missing:
        raise RuntimeError(".env 누락: " + ", ".join(missing))

    stats = {"mapped_ingredients": 0, "series_created": 0, "prices_inserted": 0,
             "duplicates": 0, "invalid_rows": 0, "api_calls": 0, "no_data_calls": 0}
    session = requests.Session()
    db_url = transaction_pool_url(env["DB_URL"])

    # Supabase transaction pooler는 세션 간 prepared statement를 공유하지 않으므로
    # psycopg의 자동 server-side prepare를 비활성화한다.
    with psycopg.connect(
        db_url,
        user=env["DB_USERNAME"],
        password=env["DB_PASSWORD"],
        prepare_threshold=None,
    ) as conn:
        with conn.cursor() as cur:
            resume_reached = args.resume_after is None
            for mapping in MAPPINGS:
                if not resume_reached:
                    if mapping.ingredient_code == args.resume_after:
                        resume_reached = True
                    continue
                cur.execute(
                    "SELECT ingredient_id, name, standard_unit FROM mealfit.ingredient "
                    "WHERE ingredient_code = %s AND is_active = true",
                    (mapping.ingredient_code,),
                )
                ingredient = cur.fetchone()
                if not ingredient:
                    print(f"SKIP missing ingredient {mapping.ingredient_code}")
                    continue
                ingredient_id, ingredient_name, standard_unit = ingredient
                if standard_unit.lower() != "g":
                    print(f"SKIP non-gram ingredient {mapping.ingredient_code}: {standard_unit}")
                    continue
                stats["mapped_ingredients"] += 1

                for region, (country_code, market) in REGIONS.items():
                    series_id, created = find_or_create_series(
                        cur, ingredient_id, mapping, region, market, args.apply
                    )
                    stats["series_created"] += int(created)
                    if not args.apply:
                        continue

                    for start, end in chunks(args.start, args.end):
                        rows = fetch_rows(session, env, mapping, country_code, start, end)
                        stats["api_calls"] += 1
                        stats["no_data_calls"] += int(not rows)
                        for row in rows:
                            if str(row.get("countyname") or "").strip() != region:
                                continue
                            price_date = parse_price_date(row.get("yyyy"), row.get("regday"))
                            price = parse_price(row.get("price"))
                            if price_date is None or price is None or not (args.start <= price_date <= args.end):
                                stats["invalid_rows"] += 1
                                continue
                            cur.execute(
                                """
                                INSERT INTO mealfit.ingredient_price
                                    (series_id, price_date, original_price, unit_quantity)
                                VALUES (%s, %s, %s, 1000)
                                ON CONFLICT (series_id, price_date) DO NOTHING
                                RETURNING price_id
                                """,
                                (series_id, price_date, price),
                            )
                            if cur.fetchone():
                                stats["prices_inserted"] += 1
                            else:
                                stats["duplicates"] += 1
                        conn.commit()
                        time.sleep(0.05)

                print(f"OK {mapping.ingredient_code} {ingredient_name}")

    mode = "APPLY" if args.apply else "DRY-RUN"
    print(mode, json.dumps(stats, ensure_ascii=False, sort_keys=True))


if __name__ == "__main__":
    main()
