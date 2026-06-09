from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    app_name: str = "Solomillo"
    database_url: str = "postgresql+psycopg://solomillo:solomillo@db:5432/solomillo"
    redis_url: str = "redis://redis:6379/0"
    jwt_secret: str = "dev-secret-change-me"
    jwt_alg: str = "HS256"
    jwt_expire_min: int = 60 * 24


settings = Settings()
