from fastapi import APIRouter

from app.api.routers.admin import router as admin_router
from app.api.routers.health import router as health_router
from app.api.routers.releases import router as releases_router
from app.api.routers.runs import router as runs_router
from app.api.routers.targets import router as targets_router

api_router = APIRouter(prefix="/api/v1")
api_router.include_router(health_router)
api_router.include_router(admin_router)
api_router.include_router(targets_router)
api_router.include_router(releases_router)
api_router.include_router(runs_router)
