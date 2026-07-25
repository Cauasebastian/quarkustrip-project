ALTER TABLE user_profiles
    ADD COLUMN username VARCHAR(255);

UPDATE user_profiles
SET username = split_part(email, '@', 1)
WHERE username IS NULL OR btrim(username) = '';

ALTER TABLE user_profiles
    ALTER COLUMN username SET NOT NULL;

CREATE INDEX idx_user_profiles_username_lower
    ON user_profiles (lower(username));
