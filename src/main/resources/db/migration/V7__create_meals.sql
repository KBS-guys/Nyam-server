CREATE TABLE meals (
    meal_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    meal_date DATE NOT NULL,
    CONSTRAINT pk_meals PRIMARY KEY (meal_id),
    CONSTRAINT fk_meals_user
        FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE,
    INDEX ix_meals_user_date_id (user_id, meal_date, meal_id)
);

CREATE TABLE meal_items (
    meal_item_id BIGINT NOT NULL AUTO_INCREMENT,
    meal_id BIGINT NOT NULL,
    item_position SMALLINT NOT NULL,
    food_id BIGINT NOT NULL,
    food_name_snapshot VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    consumed_amount DECIMAL(12,4) NOT NULL,
    consumed_unit VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    energy_snapshot DECIMAL(16,4) NULL,
    energy_unit VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    carbohydrate_snapshot DECIMAL(16,4) NULL,
    carbohydrate_unit VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    protein_snapshot DECIMAL(16,4) NULL,
    protein_unit VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    fat_snapshot DECIMAL(16,4) NULL,
    fat_unit VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    CONSTRAINT pk_meal_items PRIMARY KEY (meal_item_id),
    CONSTRAINT uk_meal_items_meal_position UNIQUE (meal_id, item_position),
    CONSTRAINT uk_meal_items_meal_food UNIQUE (meal_id, food_id),
    CONSTRAINT ck_meal_items_position CHECK (item_position BETWEEN 1 AND 20),
    CONSTRAINT ck_meal_items_food_name CHECK (CHAR_LENGTH(TRIM(food_name_snapshot)) > 0),
    CONSTRAINT ck_meal_items_consumed_amount
        CHECK (consumed_amount > 0.0000 AND consumed_amount <= 10000.0000),
    CONSTRAINT ck_meal_items_consumed_unit CHECK (consumed_unit IN ('G', 'ML')),
    CONSTRAINT ck_meal_items_energy_unit CHECK (energy_unit = 'KCAL'),
    CONSTRAINT ck_meal_items_carbohydrate_unit CHECK (carbohydrate_unit = 'G'),
    CONSTRAINT ck_meal_items_protein_unit CHECK (protein_unit = 'G'),
    CONSTRAINT ck_meal_items_fat_unit CHECK (fat_unit = 'G'),
    CONSTRAINT ck_meal_items_energy CHECK (energy_snapshot IS NULL OR energy_snapshot >= 0),
    CONSTRAINT ck_meal_items_carbohydrate
        CHECK (carbohydrate_snapshot IS NULL OR carbohydrate_snapshot >= 0),
    CONSTRAINT ck_meal_items_protein CHECK (protein_snapshot IS NULL OR protein_snapshot >= 0),
    CONSTRAINT ck_meal_items_fat CHECK (fat_snapshot IS NULL OR fat_snapshot >= 0),
    CONSTRAINT fk_meal_items_meal
        FOREIGN KEY (meal_id) REFERENCES meals (meal_id) ON DELETE CASCADE,
    CONSTRAINT fk_meal_items_food
        FOREIGN KEY (food_id) REFERENCES foods (food_id) ON DELETE RESTRICT
);
