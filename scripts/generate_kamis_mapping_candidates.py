"""기존 KAMIS 대표 시계열과 이름이 유사한 MealFit 식재료 매핑 후보를 만든다."""

from __future__ import annotations

import argparse
import re
from decimal import Decimal
from pathlib import Path

import psycopg

from backfill_kamis_mapped_prices import read_env, transaction_pool_url


STATE_WORDS = {"생것", "삶은것", "데친것", "구운것", "말린것", "냉동", "냉장", "가루"}
RAW_PROXY_WORDS = {"삶은것", "데친것", "구운것", "찐것", "튀긴것", "통조림"}
ROOT_ALIASES = {
    "멥쌀": "쌀", "찹쌀": "쌀", "대두": "콩", "귤": "감귤",
    "소고기": "소고기", "돼지고기": "돼지고기", "닭고기": "닭고기",
    "달걀": "계란", "담치": "홍합", "들깻잎": "깻잎",
    "큰느타리버섯": "새송이버섯", "오징어": "물오징어",
}


def tokens(name: str) -> list[str]:
    return [part.strip() for part in re.split(r"[,()]", name) if part.strip()]


def root(name: str) -> str:
    first = tokens(name)[0]
    return ROOT_ALIASES.get(first, first)


def mapping_score(target: str, source: str):
    if root(target) != root(source):
        return None
    target_tokens = set(tokens(target)) - STATE_WORDS
    source_tokens = set(tokens(source)) - STATE_WORDS
    overlap = len(target_tokens & source_tokens)
    mapping_type = "RAW_PROXY" if any(word in target for word in RAW_PROXY_WORDS) else "VARIETY"
    confidence = Decimal("0.72") + Decimal("0.04") * min(overlap, 3)
    if mapping_type == "RAW_PROXY":
        confidence -= Decimal("0.10")
    return mapping_type, min(confidence, Decimal("0.88"))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    env = read_env(Path(__file__).resolve().parents[1] / ".env")
    url = transaction_pool_url(env["DB_URL"])
    candidate_ingredients = 0
    candidate_rows = 0

    with psycopg.connect(url, user=env["DB_USERNAME"], password=env["DB_PASSWORD"],
                         prepare_threshold=None) as conn:
        with conn.cursor() as cur:
            cur.execute("""
                SELECT i.ingredient_id, i.ingredient_code, i.name
                FROM mealfit.ingredient i
                WHERE i.is_active = TRUE AND i.standard_unit = 'g'
                  AND NOT EXISTS (
                    SELECT 1 FROM mealfit.ingredient_price_mapping m
                    WHERE m.ingredient_id = i.ingredient_id
                      AND m.review_status = 'APPROVED' AND m.is_active = TRUE)
                ORDER BY i.ingredient_id
            """)
            ingredients = cur.fetchall()
            cur.execute("""
                SELECT owner.name, ps.source_category_code, ps.source_item_code,
                       ps.source_kind_code, ps.source_rank_code,
                       array_agg(ps.series_id ORDER BY ps.series_id)
                FROM mealfit.price_series ps
                JOIN mealfit.ingredient owner ON owner.ingredient_id = ps.ingredient_id
                WHERE ps.source_name = 'KAMIS'
                GROUP BY owner.name, ps.source_category_code, ps.source_item_code,
                         ps.source_kind_code, ps.source_rank_code
            """)
            sources = cur.fetchall()

            for ingredient_id, ingredient_code, ingredient_name in ingredients:
                ranked = []
                for source_name, *source_fields in sources:
                    score = mapping_score(ingredient_name, source_name)
                    if score:
                        ranked.append((score[1], score[0], source_name, source_fields[-1]))
                if not ranked:
                    continue
                confidence, mapping_type, source_name, series_ids = max(ranked, key=lambda row: row[0])
                candidate_ingredients += 1
                candidate_rows += len(series_ids)
                print(f"{ingredient_code}\t{ingredient_name}\t{mapping_type}\t{confidence}\t{source_name}")
                if args.apply:
                    for series_id in series_ids:
                        cur.execute("""
                            INSERT INTO mealfit.ingredient_price_mapping
                                (ingredient_id, series_id, mapping_type, conversion_factor,
                                 confidence_score, priority, review_status, is_active)
                            VALUES (%s, %s, %s, 1, %s, 50, 'PROPOSED', FALSE)
                            ON CONFLICT (ingredient_id, series_id) DO NOTHING
                        """, (ingredient_id, series_id, mapping_type, confidence))
            if args.apply:
                conn.commit()

    print(f"candidate_ingredients={candidate_ingredients} candidate_rows={candidate_rows} apply={args.apply}")


if __name__ == "__main__":
    main()
