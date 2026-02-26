from fastapi import APIRouter

from app.modules.schemas import HealthResponse

root_router = APIRouter(tags=["health"])
router = APIRouter(prefix="/health", tags=["health"])


@root_router.get("/healthz", response_model=HealthResponse)
def healthz() -> HealthResponse:
    return HealthResponse(status="ok")


@router.get("", response_model=HealthResponse)
def health() -> HealthResponse:
    return HealthResponse(status="ok")
