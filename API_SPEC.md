# MealFit API 명세서

- 문서 버전: 1.1
- 작성 기준일: 2026-09-17
- 기준 코드: 현재 `backend` 저장소의 실제 구현
- 로컬 Base URL: `http://localhost:8080`
- 데이터 형식: `application/json`

> 이 문서는 현재 구현된 API만 다룹니다. 가격 예측, 추천, AI/RAG, 관리자 API는 아직 포함되지 않습니다.

## 1. 공통 규칙

### 1.1 인증 방식

인증이 필요한 API는 로그인 응답으로 받은 JWT를 `Authorization` 헤더에 전달합니다.

```http
Authorization: Bearer {accessToken}
```

- JWT 기본 만료 시간은 3,600초입니다.
- 서버 세션을 사용하지 않는 Stateless 방식입니다.
- 토큰이 없거나 잘못되었거나 만료되면 보호 API는 `401 Unauthorized`를 반환합니다.
- 현재 로그아웃은 서버 토큰 폐기가 아니라 클라이언트에 저장된 토큰을 삭제하는 방식입니다.

### 1.2 공개 API와 인증 API

| 구분 | API |
|---|---|
| 공개 | `GET /api/health` |
| 공개 | `GET /api/health/db` |
| 공개 | `GET /api/auth/email-check` |
| 공개 | `POST /api/auth/signup` |
| 공개 | `POST /api/auth/login` |
| 인증 필요 | `GET /api/auth/me` |
| 인증 필요 | `POST /api/auth/logout` |
| 인증 필요 | `/api/facilities/**` 전체 |
| 인증 필요 | `/api/cost/**` 전체 |
| 인증 필요 | `GET /api/menus` |
| 인증 필요 | `/api/meal-plans/**` 전체 |

### 1.3 공통 오류 응답

애플리케이션 오류와 요청 본문 검증 오류는 다음 형식을 사용합니다.

```json
{
  "timestamp": "2026-09-17T03:00:00Z",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "입력값을 확인해주세요.",
  "fieldErrors": {
    "email": "올바른 형식의 이메일 주소여야 합니다"
  }
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `timestamp` | ISO-8601 문자열 | 오류 발생 시각 |
| `status` | number | HTTP 상태 코드 |
| `code` | string | 클라이언트 분기용 오류 코드 |
| `message` | string | 사용자에게 표시할 수 있는 오류 설명 |
| `fieldErrors` | object | 필드별 검증 오류. 없으면 빈 객체 |

> 현재 Spring Security에서 직접 발생하는 `401 Unauthorized`는 위 JSON 구조가 아닌 기본 오류 응답일 수 있습니다.

### 1.4 주요 상태 코드

| 상태 코드 | 의미 |
|---|---|
| `200 OK` | 조회 또는 변경 성공 |
| `201 Created` | 리소스 생성 성공 |
| `204 No Content` | 응답 본문 없는 성공 |
| `400 Bad Request` | 요청값 형식 또는 검증 실패 |
| `401 Unauthorized` | 인증 토큰 없음, 만료 또는 사용자 확인 실패 |
| `404 Not Found` | 요청한 사용자 소속 시설 등이 없음 |
| `409 Conflict` | 이메일 또는 시설이 이미 등록됨 |
| `500 Internal Server Error` | 처리되지 않은 서버 오류 |

---

## 2. API 목록

| 영역 | Method | 경로 | 인증 | 설명 |
|---|---|---|---|---|
| 상태 | GET | `/api/health` | 불필요 | 애플리케이션 상태 확인 |
| 상태 | GET | `/api/health/db` | 불필요 | DB 연결 상태 확인 |
| 인증 | GET | `/api/auth/email-check` | 불필요 | 이메일 사용 가능 여부 확인 |
| 인증 | POST | `/api/auth/signup` | 불필요 | 이메일 회원가입 |
| 인증 | POST | `/api/auth/login` | 불필요 | 로그인 및 JWT 발급 |
| 인증 | GET | `/api/auth/me` | 필요 | 현재 로그인 사용자 조회 |
| 인증 | POST | `/api/auth/logout` | 필요 | 로그아웃 응답 |
| 시설 | POST | `/api/facilities` | 필요 | 사용자 소속 시설 생성 |
| 시설 | GET | `/api/facilities/me` | 필요 | 내 시설 조회 |
| 시설 | PATCH | `/api/facilities/me` | 필요 | 내 시설 수정 |
| 원가 | GET | `/api/cost/menus` | 필요 | 전체 메뉴 원가 계산 |
| 원가 | GET | `/api/cost/menus/{menuId}` | 필요 | 단일 메뉴 원가 계산 |

---

## 3. 상태 확인 API

### 3.1 애플리케이션 상태 확인

```http
GET /api/health
```

서버 프로세스가 요청을 처리할 수 있는지 확인합니다. DB 쿼리는 실행하지 않습니다.

#### 성공 응답

```http
200 OK
```

```json
{
  "status": "UP"
}
```

### 3.2 데이터베이스 상태 확인

```http
GET /api/health/db
```

현재 DataSource를 통해 `SELECT 1`을 실행하여 DB 연결을 확인합니다.

#### 성공 응답

```http
200 OK
```

```json
{
  "status": "UP"
}
```

#### 오류

DB 연결 또는 쿼리에 실패하면 현재 구현상 `500 INTERNAL_ERROR`가 반환됩니다.

---

## 4. 인증 API

### 4.1 이메일 중복 확인

```http
GET /api/auth/email-check?email={email}
```

회원가입 전에 정규화된 이메일의 사용 가능 여부를 확인합니다. 이메일은 앞뒤 공백을 제거하고 소문자로 변환합니다.

#### Query Parameter

| 이름 | 타입 | 필수 | 검증 |
|---|---|---|---|
| `email` | string | 필수 | 빈 값 불가, 이메일 형식 |

#### 요청 예시

```http
GET /api/auth/email-check?email=user@example.com
```

#### 성공 응답

```http
200 OK
```

```json
{
  "email": "user@example.com",
  "available": true
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `email` | string | 정규화된 이메일 |
| `available` | boolean | `true`이면 가입 가능 |

### 4.2 회원가입

```http
POST /api/auth/signup
Content-Type: application/json
```

신규 사용자를 생성합니다. 비밀번호는 BCrypt 해시로 변환되어 저장되며 응답에는 포함되지 않습니다. 신규 사용자의 기본 권한은 `USER`, 상태는 `ACTIVE`입니다.

#### 요청 본문

```json
{
  "email": "user@example.com",
  "password": "password123",
  "name": "홍길동"
}
```

| 필드 | 타입 | 필수 | 검증 |
|---|---|---|---|
| `email` | string | 필수 | 이메일 형식, 최대 150자 |
| `password` | string | 필수 | 8~72자 |
| `name` | string | 필수 | 공백만 입력 불가, 최대 50자 |

#### 성공 응답

```http
201 Created
```

```json
{
  "userId": 1,
  "email": "user@example.com",
  "name": "홍길동",
  "role": "USER",
  "facilityId": null
}
```

#### 오류

| 상태 | 코드 | 발생 조건 |
|---|---|---|
| `400` | `VALIDATION_ERROR` | 필수값, 이메일 형식 또는 길이 검증 실패 |
| `409` | `EMAIL_ALREADY_EXISTS` | 같은 이메일이 이미 존재함 |

### 4.3 로그인

```http
POST /api/auth/login
Content-Type: application/json
```

이메일과 비밀번호를 검증하고 JWT를 발급합니다. 비활성 사용자와 잘못된 비밀번호는 동일한 오류를 반환하여 계정 존재 여부를 노출하지 않습니다.

#### 요청 본문

```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

| 필드 | 타입 | 필수 | 검증 |
|---|---|---|---|
| `email` | string | 필수 | 이메일 형식 |
| `password` | string | 필수 | 빈 값 불가 |

#### 성공 응답

```http
200 OK
```

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "user": {
    "userId": 1,
    "email": "user@example.com",
    "name": "홍길동",
    "role": "USER",
    "facilityId": 1
  }
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `accessToken` | string | 이후 요청에 사용할 JWT |
| `tokenType` | string | 항상 `Bearer` |
| `expiresIn` | number | 토큰 만료까지 남은 초 단위 시간 |
| `user` | object | 로그인 사용자 공개 정보 |

#### 오류

| 상태 | 코드 | 발생 조건 |
|---|---|---|
| `400` | `VALIDATION_ERROR` | 요청 형식 검증 실패 |
| `401` | `INVALID_CREDENTIALS` | 이메일 또는 비밀번호 오류, 비활성 사용자 |

### 4.4 현재 사용자 조회

```http
GET /api/auth/me
Authorization: Bearer {accessToken}
```

JWT로 인증된 현재 사용자를 조회합니다.

#### 성공 응답

```http
200 OK
```

```json
{
  "userId": 1,
  "email": "user@example.com",
  "name": "홍길동",
  "role": "USER",
  "facilityId": 1
}
```

#### 오류

| 상태 | 코드 | 발생 조건 |
|---|---|---|
| `401` | 보안 기본 응답 | 토큰 없음, 토큰 만료 또는 잘못된 토큰 |
| `401` | `USER_NOT_FOUND` | 토큰의 사용자가 DB에 존재하지 않음 |

### 4.5 로그아웃

```http
POST /api/auth/logout
Authorization: Bearer {accessToken}
```

#### 성공 응답

```http
204 No Content
```

응답 본문은 없습니다. 현재 서버는 토큰 차단 목록을 관리하지 않으므로 클라이언트가 저장한 JWT를 삭제해야 합니다.

---

## 5. 시설 API

### 5.1 시설 요청·응답 모델

#### 요청 본문

```json
{
  "name": "한빛초등학교",
  "facilityType": "SCHOOL",
  "address": "서울특별시 예시구",
  "contactName": "홍길동",
  "defaultMealCount": 100,
  "breakfastMealCount": 0,
  "lunchMealCount": 100,
  "dinnerMealCount": 0,
  "targetFoodCost": 5000.00
}
```

| 필드 | 타입 | 필수 | 검증·설명 |
|---|---|---|---|
| `name` | string | 필수 | 공백만 입력 불가, 최대 100자 |
| `facilityType` | string | 필수 | 공백만 입력 불가, 최대 30자. 현재 Enum 제한 없음 |
| `address` | string/null | 선택 | 최대 255자 |
| `contactName` | string/null | 선택 | 최대 50자 |
| `defaultMealCount` | integer | 필수 | 0 이상 |
| `breakfastMealCount` | integer/null | 선택 | 0 이상 |
| `lunchMealCount` | integer/null | 선택 | 0 이상 |
| `dinnerMealCount` | integer/null | 선택 | 0 이상 |
| `targetFoodCost` | decimal | 필수 | 0.00 이상 |

#### 응답 본문

```json
{
  "facilityId": 1,
  "name": "한빛초등학교",
  "facilityType": "SCHOOL",
  "address": "서울특별시 예시구",
  "contactName": "홍길동",
  "defaultMealCount": 100,
  "breakfastMealCount": 0,
  "lunchMealCount": 100,
  "dinnerMealCount": 0,
  "targetFoodCost": 5000.00
}
```

### 5.2 내 시설 생성

```http
POST /api/facilities
Authorization: Bearer {accessToken}
Content-Type: application/json
```

현재 사용자에게 소속 시설을 새로 생성하고 배정합니다. 사용자에게 이미 시설이 있으면 중복 생성할 수 없습니다.

#### 성공 응답

```http
201 Created
```

응답 본문은 시설 응답 모델과 같습니다.

#### 오류

| 상태 | 코드 | 발생 조건 |
|---|---|---|
| `400` | `VALIDATION_ERROR` | 요청 필드 검증 실패 |
| `401` | 인증 오류 | 인증되지 않은 요청 |
| `401` | `USER_NOT_FOUND` | 인증 사용자가 DB에 없음 |
| `409` | `FACILITY_ALREADY_ASSIGNED` | 사용자에게 시설이 이미 배정됨 |

### 5.3 내 시설 조회

```http
GET /api/facilities/me
Authorization: Bearer {accessToken}
```

#### 성공 응답

```http
200 OK
```

응답 본문은 시설 응답 모델과 같습니다.

#### 오류

| 상태 | 코드 | 발생 조건 |
|---|---|---|
| `401` | 인증 오류 또는 `USER_NOT_FOUND` | 인증 실패 또는 사용자 없음 |
| `404` | `FACILITY_NOT_FOUND` | 사용자에게 등록된 시설이 없음 |

### 5.4 내 시설 수정

```http
PATCH /api/facilities/me
Authorization: Bearer {accessToken}
Content-Type: application/json
```

현재 사용자의 시설 정보를 수정합니다. PATCH 메서드를 사용하지만 현재 구현에서는 시설 요청 모델의 필수 필드를 모두 보내야 합니다.

#### 성공 응답

```http
200 OK
```

응답 본문은 수정된 시설 응답 모델과 같습니다.

#### 오류

| 상태 | 코드 | 발생 조건 |
|---|---|---|
| `400` | `VALIDATION_ERROR` | 요청 필드 검증 실패 |
| `401` | 인증 오류 또는 `USER_NOT_FOUND` | 인증 실패 또는 사용자 없음 |
| `404` | `FACILITY_NOT_FOUND` | 사용자에게 등록된 시설이 없음 |

---

## 6. 메뉴 원가 API

> 현재 원가 API는 Supabase가 아닌 `MemoryCostRepository`의 더미 메뉴 101, 102를 사용합니다. DB 연동 전 개발·시연용 API입니다.

### 6.1 원가 응답 모델

```json
{
  "menuId": 101,
  "menuName": "돼지고기 김치찌개",
  "costPerPerson": 2680.0,
  "mealCount": 50,
  "totalMealCost": 134000.0,
  "targetCost": 3000,
  "exceeded": false,
  "exceededAmount": 0,
  "details": [
    {
      "ingredientId": 1,
      "ingredientName": "돼지고기(전지)",
      "quantity": 100,
      "standardUnitPrice": 14.5,
      "lineCost": 1450.0,
      "priceDate": "2026-09-15"
    }
  ]
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `menuId` | number | 메뉴 ID |
| `menuName` | string | 메뉴명 |
| `costPerPerson` | decimal | 1인분 현재 원가 |
| `mealCount` | integer | 계산에 사용한 식수 인원 |
| `totalMealCost` | decimal | `costPerPerson × mealCount` |
| `targetCost` | decimal | 비교 기준 1인 목표 원가 |
| `exceeded` | boolean | 1인 원가가 목표 원가를 초과했는지 여부 |
| `exceededAmount` | decimal | 목표 원가 초과액. 미초과 시 0 |
| `details` | array | 메뉴에 포함된 식재료별 원가 상세 |

> Java 필드명은 `isExceeded`이지만 Jackson 직렬화 결과는 일반적으로 `exceeded`로 표현됩니다. 실제 연동 시 응답 JSON을 다시 확인해야 합니다.

### 6.2 전체 메뉴 원가 조회

```http
GET /api/cost/menus?mealCount={mealCount}&targetCost={targetCost}
Authorization: Bearer {accessToken}
```

#### Query Parameter

| 이름 | 타입 | 필수 | 기본값·처리 |
|---|---|---|---|
| `mealCount` | integer | 선택 | 기본값 1. 0 이하도 1로 보정 |
| `targetCost` | decimal | 선택 | 미입력 또는 0 이하는 2,500원 적용 |

#### 요청 예시

```http
GET /api/cost/menus?mealCount=50&targetCost=3000
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

#### 성공 응답

```http
200 OK
```

원가 응답 모델의 배열을 반환합니다.

### 6.3 단일 메뉴 원가 조회

```http
GET /api/cost/menus/{menuId}?mealCount={mealCount}&targetCost={targetCost}
Authorization: Bearer {accessToken}
```

#### Path Parameter

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `menuId` | number | 필수 | 현재 더미 데이터에서는 `101` 또는 `102` |

Query Parameter 규칙은 전체 메뉴 원가 조회와 같습니다.

#### 요청 예시

```http
GET /api/cost/menus/101?mealCount=50&targetCost=3000
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

#### 성공 응답

```http
200 OK
```

원가 응답 모델 하나를 반환합니다.

#### 현재 구현상의 오류

- 존재하지 않는 `menuId`: `IllegalArgumentException`이 공통 처리되어 `500 INTERNAL_ERROR`
- 메뉴에 식재료 정보가 없음: `IllegalStateException`이 공통 처리되어 `500 INTERNAL_ERROR`

> 두 경우는 추후 각각 `404 MENU_NOT_FOUND`, `422 MENU_INGREDIENTS_NOT_FOUND` 같은 업무 오류로 개선하는 것을 권장합니다.

---

## 7. 메뉴 구성 API

> 현재 메뉴 API는 `MemoryCostRepository`의 더미 메뉴 101, 102와 식재료 정보를 사용합니다. 메뉴 데이터베이스 연동 전 개발·시연용 API입니다.

### 7.1 메뉴 및 식재료 조회

```http
GET /api/menus?slot={slot}
Authorization: Bearer {accessToken}
```

`slot`은 선택값입니다. 생략하면 전체 메뉴를 반환합니다.

| 값 | 설명 |
|---|---|
| `RICE` | 밥 |
| `SOUP` | 국·찌개 |
| `MAIN` | 주찬 |
| `SIDE` | 부찬 |
| `KIMCHI` | 김치 |
| `OTHER` | 기타 |

#### 성공 응답

```http
200 OK
```

```json
[
  {
    "menuId": 101,
    "menuCode": "MENU-101",
    "menuName": "돼지고기 김치찌개",
    "mainCategory": "국",
    "subCategory": "김치",
    "slot": "SOUP",
    "weight": null,
    "foodCount": 3,
    "ingredients": [
      {
        "ingredientId": 1,
        "ingredientName": "돼지고기(전지)",
        "quantity": 100,
        "unitPrice": 14.5,
        "priceDate": "2026-09-15"
      }
    ]
  }
]
```

> 현재 슬롯은 메뉴명 기반 임시 분류입니다. 실제 메뉴 데이터와 연결할 때 메뉴별 슬롯을 저장하는 방식으로 변경해야 합니다.

---

## 8. 주간 식단 API

> 현재 식단 API는 데이터베이스가 아닌 서버 메모리에 저장합니다. 서버를 재시작하면 저장된 식단이 사라집니다. 모든 엔드포인트는 인증이 필요합니다.

### 8.1 식단 저장

```http
POST /api/meal-plans
Authorization: Bearer {accessToken}
Content-Type: application/json
```

#### 요청 본문

```json
{
  "weekStartDate": "2026-09-21",
  "mealCount": 100,
  "meals": [
    {
      "mealDate": "2026-09-21",
      "slot": "SOUP",
      "menuId": 101
    }
  ]
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `weekStartDate` | date | 필수 | 주간 식단의 시작일 |
| `mealCount` | integer | 선택 | 기본값 1, 1 이상 |
| `meals` | array | 필수 | 비어 있지 않은 식단 항목 목록 |
| `meals[].mealDate` | date | 선택 | 메뉴가 배정된 날짜 |
| `meals[].slot` | enum | 선택 | `RICE`, `SOUP`, `MAIN`, `SIDE`, `KIMCHI`, `OTHER` |
| `meals[].menuId` | number | 선택 | 메뉴 ID |

#### 성공 응답

```http
201 Created
```

응답은 `weekStartDate`, `mealCount`, `totalCost`, `meals`를 포함합니다.

### 8.2 주간 식단 조회

```http
GET /api/meal-plans/weekly?weekStartDate=2026-09-21
Authorization: Bearer {accessToken}
```

#### 성공 응답

```http
200 OK
```

저장된 주간 식단과 메뉴별 1인 원가를 반환합니다. 저장된 식단이 없으면 현재 구현상 `500 INTERNAL_ERROR`가 반환됩니다.

### 8.3 주간 식단 재구성

```http
POST /api/meal-plans/reconfigure
Authorization: Bearer {accessToken}
Content-Type: application/json
```

#### 요청 본문

```json
{
  "weekStartDate": "2026-09-21",
  "mealCount": 100,
  "targetCost": 2500
}
```

현재 구현은 목표 원가 이하 메뉴를 우선 선택하고, 바로 전날과 같은 메뉴의 반복을 피하면서 7일 식단을 생성합니다. 가격 예측 AI와 메뉴 다양성 분석은 아직 연결되지 않았습니다.

---

## 9. 호출 흐름 예시

### 7.1 신규 사용자

```text
1. GET  /api/auth/email-check
2. POST /api/auth/signup
3. POST /api/auth/login
4. POST /api/facilities
5. GET  /api/auth/me
6. GET  /api/cost/menus
```

### 7.2 로그인 후 시설 조회

```bash
curl -X POST "http://localhost:8080/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"password123"}'

curl "http://localhost:8080/api/facilities/me" \
  -H "Authorization: Bearer {accessToken}"
```

---

## 10. 역할과 권한

현재 사용자 역할은 다음 세 값으로 정의되어 있습니다.

| 역할 | 설명 |
|---|---|
| `USER` | 회원가입 시 부여되는 기본 역할 |
| `MANAGER` | 관리자 확장용 역할 |
| `ADMIN` | 시스템 관리자 확장용 역할 |

현재 보안 설정은 공개 API와 인증 필요 API만 구분하며, `MANAGER` 또는 `ADMIN` 역할별 엔드포인트 제한은 아직 구현되지 않았습니다.

---

## 11. 현재 구현 범위와 후속 작업

### 구현됨

- 서버 및 DB 헬스체크
- 이메일 중복 확인
- 회원가입과 BCrypt 비밀번호 저장
- 로그인과 JWT 발급
- 현재 사용자 조회
- 클라이언트 토큰 삭제 방식 로그아웃
- 사용자 소속 시설 생성·조회·수정
- 메모리 데이터 기반 메뉴 원가 계산
- 메모리 데이터 기반 메뉴·식재료 조회 및 슬롯 분류
- 메모리 기반 주간 식단 저장·조회·재구성

### 아직 구현되지 않음

- 관리자 API와 역할별 접근 제어
- 실제 DB 기반 메뉴·식재료·가격 조회
- 가격 수집과 가격 예측 API
- DB 기반 주간·월간 식단 저장
- 예산 분석과 대체 메뉴 추천
- FastAPI AI/RAG 연동
- Refresh Token 및 서버 측 토큰 폐기
- OpenAPI/Swagger 자동 문서

### 문서 갱신 규칙

컨트롤러의 경로, DTO 필드, 검증 조건 또는 오류 코드가 변경되면 이 문서를 같은 변경사항에 포함해 갱신합니다.
