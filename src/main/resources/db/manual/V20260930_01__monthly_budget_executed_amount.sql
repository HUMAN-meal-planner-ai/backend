ALTER TABLE mealfit.monthly_budget
    ADD COLUMN IF NOT EXISTS executed_amount NUMERIC(15, 2);