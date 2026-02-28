from typing import Optional
import datetime

from sqlalchemy import Boolean, DateTime, Index, Integer, PrimaryKeyConstraint, String, Text, text
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column

class Base(DeclarativeBase):
    pass


class FlywaySchemaHistory(Base):
    __tablename__ = 'flyway_schema_history'
    __table_args__ = (
        PrimaryKeyConstraint('installed_rank', name='flyway_schema_history_pk'),
        Index('flyway_schema_history_s_idx', 'success')
    )

    installed_rank: Mapped[int] = mapped_column(Integer, primary_key=True)
    description: Mapped[str] = mapped_column(String(200), nullable=False)
    type: Mapped[str] = mapped_column(String(20), nullable=False)
    script: Mapped[str] = mapped_column(String(1000), nullable=False)
    installed_by: Mapped[str] = mapped_column(String(100), nullable=False)
    installed_on: Mapped[datetime.datetime] = mapped_column(DateTime, nullable=False, server_default=text('now()'))
    execution_time: Mapped[int] = mapped_column(Integer, nullable=False)
    success: Mapped[bool] = mapped_column(Boolean, nullable=False)
    version: Mapped[Optional[str]] = mapped_column(String(50))
    checksum: Mapped[Optional[int]] = mapped_column(Integer)


class Releases(Base):
    __tablename__ = 'releases'
    __table_args__ = (
        PrimaryKeyConstraint('id', name='releases_pkey'),
        Index('idx_releases_created_at', 'created_at')
    )

    id: Mapped[str] = mapped_column(Text, primary_key=True)
    payload_json: Mapped[dict] = mapped_column(JSONB, nullable=False)
    created_at: Mapped[datetime.datetime] = mapped_column(DateTime(True), nullable=False, server_default=text('now()'))


class Runs(Base):
    __tablename__ = 'runs'
    __table_args__ = (
        PrimaryKeyConstraint('id', name='runs_pkey'),
        Index('idx_runs_created_at', 'created_at'),
        Index('idx_runs_updated_at', 'updated_at')
    )

    id: Mapped[str] = mapped_column(Text, primary_key=True)
    payload_json: Mapped[dict] = mapped_column(JSONB, nullable=False)
    created_at: Mapped[datetime.datetime] = mapped_column(DateTime(True), nullable=False)
    updated_at: Mapped[datetime.datetime] = mapped_column(DateTime(True), nullable=False, server_default=text('now()'))


class Targets(Base):
    __tablename__ = 'targets'
    __table_args__ = (
        PrimaryKeyConstraint('id', name='targets_pkey'),
    )

    id: Mapped[str] = mapped_column(Text, primary_key=True)
    payload_json: Mapped[dict] = mapped_column(JSONB, nullable=False)
    updated_at: Mapped[datetime.datetime] = mapped_column(DateTime(True), nullable=False, server_default=text('now()'))


class TargetRegistrations(Base):
    __tablename__ = 'target_registrations'
    __table_args__ = (
        PrimaryKeyConstraint('id', name='target_registrations_pkey'),
        Index('idx_target_registrations_updated_at', 'updated_at')
    )

    id: Mapped[str] = mapped_column(Text, primary_key=True)
    payload_json: Mapped[dict] = mapped_column(JSONB, nullable=False)
    updated_at: Mapped[datetime.datetime] = mapped_column(DateTime(True), nullable=False, server_default=text('now()'))


class MarketplaceEvents(Base):
    __tablename__ = 'marketplace_events'
    __table_args__ = (
        PrimaryKeyConstraint('id', name='marketplace_events_pkey'),
        Index('idx_marketplace_events_created_at', 'created_at')
    )

    id: Mapped[str] = mapped_column(Text, primary_key=True)
    payload_json: Mapped[dict] = mapped_column(JSONB, nullable=False)
    created_at: Mapped[datetime.datetime] = mapped_column(DateTime(True), nullable=False, server_default=text('now()'))
