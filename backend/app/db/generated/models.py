from typing import Optional
import datetime
import uuid

from sqlalchemy import Boolean, DateTime, Double, Enum, ForeignKeyConstraint, Index, Integer, PrimaryKeyConstraint, String, Text, Uuid, text
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, relationship

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


class ForwarderLogs(Base):
    __tablename__ = 'forwarder_logs'
    __table_args__ = (
        PrimaryKeyConstraint('id', name='forwarder_logs_pkey'),
        Index('idx_forwarder_logs_created_at', 'created_at')
    )

    id: Mapped[str] = mapped_column(Text, primary_key=True)
    level: Mapped[str] = mapped_column(Enum('info', 'warning', 'error', name='mappo_forwarder_log_level'), nullable=False)
    message: Mapped[str] = mapped_column(Text, nullable=False)
    details: Mapped[dict] = mapped_column(JSONB, nullable=False, server_default=text("'{}'::jsonb"))
    created_at: Mapped[datetime.datetime] = mapped_column(DateTime(True), nullable=False, server_default=text('now()'))
    event_id: Mapped[Optional[str]] = mapped_column(Text)
    event_type: Mapped[Optional[str]] = mapped_column(Text)
    target_id: Mapped[Optional[str]] = mapped_column(Text)
    tenant_id: Mapped[Optional[uuid.UUID]] = mapped_column(Uuid)
    subscription_id: Mapped[Optional[uuid.UUID]] = mapped_column(Uuid)
    function_app_name: Mapped[Optional[str]] = mapped_column(Text)
    forwarder_request_id: Mapped[Optional[str]] = mapped_column(Text)
    backend_status_code: Mapped[Optional[int]] = mapped_column(Integer)


class MarketplaceEvents(Base):
    __tablename__ = 'marketplace_events'
    __table_args__ = (
        PrimaryKeyConstraint('id', name='marketplace_events_pkey'),
        Index('idx_marketplace_events_created_at', 'created_at')
    )

    id: Mapped[str] = mapped_column(Text, primary_key=True)
    event_type: Mapped[str] = mapped_column(Text, nullable=False)
    status: Mapped[str] = mapped_column(Enum('applied', 'duplicate', 'rejected', name='mappo_marketplace_event_status'), nullable=False)
    message: Mapped[str] = mapped_column(Text, nullable=False)
    tenant_id: Mapped[uuid.UUID] = mapped_column(Uuid, nullable=False)
    subscription_id: Mapped[uuid.UUID] = mapped_column(Uuid, nullable=False)
    payload: Mapped[dict] = mapped_column(JSONB, nullable=False, server_default=text("'{}'::jsonb"))
    created_at: Mapped[datetime.datetime] = mapped_column(DateTime(True), nullable=False)
    target_id: Mapped[Optional[str]] = mapped_column(Text)
    processed_at: Mapped[Optional[datetime.datetime]] = mapped_column(DateTime(True))


class Releases(Base):
    __tablename__ = 'releases'
    __table_args__ = (
        PrimaryKeyConstraint('id', name='releases_pkey'),
        Index('idx_releases_created_at', 'created_at')
    )

    id: Mapped[str] = mapped_column(Text, primary_key=True)
    template_spec_id: Mapped[str] = mapped_column(Text, nullable=False)
    template_spec_version: Mapped[str] = mapped_column(Text, nullable=False)
    release_notes: Mapped[str] = mapped_column(Text, nullable=False, server_default=text("''::text"))
    created_at: Mapped[datetime.datetime] = mapped_column(DateTime(True), nullable=False, server_default=text('now()'))
    deployment_mode: Mapped[str] = mapped_column(Enum('container_patch', 'template_spec', name='mappo_deployment_mode'), nullable=False, server_default=text("'container_patch'::mappo_deployment_mode"))
    deployment_scope: Mapped[str] = mapped_column(Enum('resource_group', 'subscription', name='mappo_deployment_scope'), nullable=False, server_default=text("'resource_group'::mappo_deployment_scope"))
    deployment_mode_settings: Mapped[dict] = mapped_column(JSONB, nullable=False, server_default=text("'{}'::jsonb"))
    template_spec_version_id: Mapped[Optional[str]] = mapped_column(Text)

    release_parameter_defaults: Mapped[list['ReleaseParameterDefaults']] = relationship('ReleaseParameterDefaults', back_populates='release')
    release_verification_hints: Mapped[list['ReleaseVerificationHints']] = relationship('ReleaseVerificationHints', back_populates='release')


class Runs(Base):
    __tablename__ = 'runs'
    __table_args__ = (
        PrimaryKeyConstraint('id', name='runs_pkey'),
        Index('idx_runs_created_at', 'created_at'),
        Index('idx_runs_updated_at', 'updated_at')
    )

    id: Mapped[str] = mapped_column(Text, primary_key=True)
    release_id: Mapped[str] = mapped_column(Text, nullable=False)
    strategy_mode: Mapped[str] = mapped_column(Enum('all_at_once', 'waves', name='mappo_strategy_mode'), nullable=False)
    wave_tag: Mapped[str] = mapped_column(Text, nullable=False)
    concurrency: Mapped[int] = mapped_column(Integer, nullable=False)
    subscription_concurrency: Mapped[int] = mapped_column(Integer, nullable=False, server_default=text('1'))
    status: Mapped[str] = mapped_column(Enum('running', 'succeeded', 'failed', 'partial', 'halted', name='mappo_run_status'), nullable=False)
    created_at: Mapped[datetime.datetime] = mapped_column(DateTime(True), nullable=False)
    updated_at: Mapped[datetime.datetime] = mapped_column(DateTime(True), nullable=False, server_default=text('now()'))
    execution_mode: Mapped[str] = mapped_column(Enum('container_patch', 'template_spec', name='mappo_deployment_mode'), nullable=False, server_default=text("'container_patch'::mappo_deployment_mode"))
    stop_policy_max_failure_count: Mapped[Optional[int]] = mapped_column(Integer)
    stop_policy_max_failure_rate: Mapped[Optional[float]] = mapped_column(Double(53))
    halt_reason: Mapped[Optional[str]] = mapped_column(Text)
    started_at: Mapped[Optional[datetime.datetime]] = mapped_column(DateTime(True))
    ended_at: Mapped[Optional[datetime.datetime]] = mapped_column(DateTime(True))

    run_guardrail_warnings: Mapped[list['RunGuardrailWarnings']] = relationship('RunGuardrailWarnings', back_populates='run')
    run_targets: Mapped[list['RunTargets']] = relationship('RunTargets', back_populates='run')
    run_wave_order: Mapped[list['RunWaveOrder']] = relationship('RunWaveOrder', back_populates='run')
    target_execution_records: Mapped[list['TargetExecutionRecords']] = relationship('TargetExecutionRecords', back_populates='run')


class TargetRegistrations(Base):
    __tablename__ = 'target_registrations'
    __table_args__ = (
        PrimaryKeyConstraint('target_id', name='target_registrations_pkey'),
        Index('idx_target_registrations_updated_at', 'updated_at')
    )

    target_id: Mapped[str] = mapped_column(Text, primary_key=True)
    display_name: Mapped[str] = mapped_column(Text, nullable=False)
    managed_resource_group_id: Mapped[str] = mapped_column(Text, nullable=False)
    metadata_: Mapped[dict] = mapped_column('metadata', JSONB, nullable=False, server_default=text("'{}'::jsonb"))
    created_at: Mapped[datetime.datetime] = mapped_column(DateTime(True), nullable=False)
    updated_at: Mapped[datetime.datetime] = mapped_column(DateTime(True), nullable=False, server_default=text('now()'))
    managed_application_id: Mapped[Optional[str]] = mapped_column(Text)
    last_event_id: Mapped[Optional[str]] = mapped_column(Text)


class Targets(Base):
    __tablename__ = 'targets'
    __table_args__ = (
        PrimaryKeyConstraint('id', name='targets_pkey'),
    )

    id: Mapped[str] = mapped_column(Text, primary_key=True)
    tenant_id: Mapped[uuid.UUID] = mapped_column(Uuid, nullable=False)
    subscription_id: Mapped[uuid.UUID] = mapped_column(Uuid, nullable=False)
    managed_app_id: Mapped[str] = mapped_column(Text, nullable=False)
    last_deployed_release: Mapped[str] = mapped_column(Text, nullable=False)
    health_status: Mapped[str] = mapped_column(Enum('registered', 'healthy', 'degraded', name='mappo_health_status'), nullable=False)
    last_check_in_at: Mapped[datetime.datetime] = mapped_column(DateTime(True), nullable=False)
    simulated_failure_mode: Mapped[str] = mapped_column(Enum('none', 'validate_once', 'deploy_once', 'verify_once', name='mappo_simulated_failure_mode'), nullable=False, server_default=text("'none'::mappo_simulated_failure_mode"))
    updated_at: Mapped[datetime.datetime] = mapped_column(DateTime(True), nullable=False, server_default=text('now()'))
    customer_name: Mapped[Optional[str]] = mapped_column(Text)

    target_tags: Mapped[list['TargetTags']] = relationship('TargetTags', back_populates='target')


class ReleaseParameterDefaults(Base):
    __tablename__ = 'release_parameter_defaults'
    __table_args__ = (
        ForeignKeyConstraint(['release_id'], ['releases.id'], ondelete='CASCADE', name='release_parameter_defaults_release_id_fkey'),
        PrimaryKeyConstraint('release_id', 'param_key', name='release_parameter_defaults_pkey')
    )

    release_id: Mapped[str] = mapped_column(Text, primary_key=True)
    param_key: Mapped[str] = mapped_column(Text, primary_key=True)
    param_value: Mapped[str] = mapped_column(Text, nullable=False)

    release: Mapped['Releases'] = relationship('Releases', back_populates='release_parameter_defaults')


class ReleaseVerificationHints(Base):
    __tablename__ = 'release_verification_hints'
    __table_args__ = (
        ForeignKeyConstraint(['release_id'], ['releases.id'], ondelete='CASCADE', name='release_verification_hints_release_id_fkey'),
        PrimaryKeyConstraint('release_id', 'position', name='release_verification_hints_pkey')
    )

    release_id: Mapped[str] = mapped_column(Text, primary_key=True)
    position: Mapped[int] = mapped_column(Integer, primary_key=True)
    hint: Mapped[str] = mapped_column(Text, nullable=False)

    release: Mapped['Releases'] = relationship('Releases', back_populates='release_verification_hints')


class RunGuardrailWarnings(Base):
    __tablename__ = 'run_guardrail_warnings'
    __table_args__ = (
        ForeignKeyConstraint(['run_id'], ['runs.id'], ondelete='CASCADE', name='run_guardrail_warnings_run_id_fkey'),
        PrimaryKeyConstraint('run_id', 'position', name='run_guardrail_warnings_pkey')
    )

    run_id: Mapped[str] = mapped_column(Text, primary_key=True)
    position: Mapped[int] = mapped_column(Integer, primary_key=True)
    warning: Mapped[str] = mapped_column(Text, nullable=False)

    run: Mapped['Runs'] = relationship('Runs', back_populates='run_guardrail_warnings')


class RunTargets(Base):
    __tablename__ = 'run_targets'
    __table_args__ = (
        ForeignKeyConstraint(['run_id'], ['runs.id'], ondelete='CASCADE', name='run_targets_run_id_fkey'),
        PrimaryKeyConstraint('run_id', 'position', name='run_targets_pkey'),
        Index('idx_run_targets_target_id', 'target_id')
    )

    run_id: Mapped[str] = mapped_column(Text, primary_key=True)
    position: Mapped[int] = mapped_column(Integer, primary_key=True)
    target_id: Mapped[str] = mapped_column(Text, nullable=False)

    run: Mapped['Runs'] = relationship('Runs', back_populates='run_targets')


class RunWaveOrder(Base):
    __tablename__ = 'run_wave_order'
    __table_args__ = (
        ForeignKeyConstraint(['run_id'], ['runs.id'], ondelete='CASCADE', name='run_wave_order_run_id_fkey'),
        PrimaryKeyConstraint('run_id', 'position', name='run_wave_order_pkey')
    )

    run_id: Mapped[str] = mapped_column(Text, primary_key=True)
    position: Mapped[int] = mapped_column(Integer, primary_key=True)
    wave_value: Mapped[str] = mapped_column(Text, nullable=False)

    run: Mapped['Runs'] = relationship('Runs', back_populates='run_wave_order')


class TargetExecutionRecords(Base):
    __tablename__ = 'target_execution_records'
    __table_args__ = (
        ForeignKeyConstraint(['run_id'], ['runs.id'], ondelete='CASCADE', name='target_execution_records_run_id_fkey'),
        PrimaryKeyConstraint('run_id', 'target_id', name='target_execution_records_pkey')
    )

    run_id: Mapped[str] = mapped_column(Text, primary_key=True)
    target_id: Mapped[str] = mapped_column(Text, primary_key=True)
    subscription_id: Mapped[uuid.UUID] = mapped_column(Uuid, nullable=False)
    tenant_id: Mapped[uuid.UUID] = mapped_column(Uuid, nullable=False)
    status: Mapped[str] = mapped_column(Enum('QUEUED', 'VALIDATING', 'DEPLOYING', 'VERIFYING', 'SUCCEEDED', 'FAILED', name='mappo_target_stage'), nullable=False)
    attempt: Mapped[int] = mapped_column(Integer, nullable=False, server_default=text('0'))
    updated_at: Mapped[datetime.datetime] = mapped_column(DateTime(True), nullable=False)

    run: Mapped['Runs'] = relationship('Runs', back_populates='target_execution_records')
    target_log_events: Mapped[list['TargetLogEvents']] = relationship('TargetLogEvents', back_populates='target_execution_records')
    target_stage_records: Mapped[list['TargetStageRecords']] = relationship('TargetStageRecords', back_populates='target_execution_records')


class TargetTags(Base):
    __tablename__ = 'target_tags'
    __table_args__ = (
        ForeignKeyConstraint(['target_id'], ['targets.id'], ondelete='CASCADE', name='target_tags_target_id_fkey'),
        PrimaryKeyConstraint('target_id', 'tag_key', name='target_tags_pkey'),
        Index('idx_target_tags_key_value', 'tag_key', 'tag_value')
    )

    target_id: Mapped[str] = mapped_column(Text, primary_key=True)
    tag_key: Mapped[str] = mapped_column(Text, primary_key=True)
    tag_value: Mapped[str] = mapped_column(Text, nullable=False)

    target: Mapped['Targets'] = relationship('Targets', back_populates='target_tags')


class TargetLogEvents(Base):
    __tablename__ = 'target_log_events'
    __table_args__ = (
        ForeignKeyConstraint(['run_id', 'target_id'], ['target_execution_records.run_id', 'target_execution_records.target_id'], ondelete='CASCADE', name='target_log_events_run_id_target_id_fkey'),
        PrimaryKeyConstraint('run_id', 'target_id', 'position', name='target_log_events_pkey')
    )

    run_id: Mapped[str] = mapped_column(Text, primary_key=True)
    target_id: Mapped[str] = mapped_column(Text, primary_key=True)
    position: Mapped[int] = mapped_column(Integer, primary_key=True)
    event_timestamp: Mapped[datetime.datetime] = mapped_column(DateTime(True), nullable=False)
    level: Mapped[str] = mapped_column(Enum('info', 'warning', 'error', name='mappo_forwarder_log_level'), nullable=False)
    stage: Mapped[str] = mapped_column(Enum('QUEUED', 'VALIDATING', 'DEPLOYING', 'VERIFYING', 'SUCCEEDED', 'FAILED', name='mappo_target_stage'), nullable=False)
    message: Mapped[str] = mapped_column(Text, nullable=False)
    correlation_id: Mapped[str] = mapped_column(Text, nullable=False)

    target_execution_records: Mapped['TargetExecutionRecords'] = relationship('TargetExecutionRecords', back_populates='target_log_events')


class TargetStageRecords(Base):
    __tablename__ = 'target_stage_records'
    __table_args__ = (
        ForeignKeyConstraint(['run_id', 'target_id'], ['target_execution_records.run_id', 'target_execution_records.target_id'], ondelete='CASCADE', name='target_stage_records_run_id_target_id_fkey'),
        PrimaryKeyConstraint('run_id', 'target_id', 'position', name='target_stage_records_pkey')
    )

    run_id: Mapped[str] = mapped_column(Text, primary_key=True)
    target_id: Mapped[str] = mapped_column(Text, primary_key=True)
    position: Mapped[int] = mapped_column(Integer, primary_key=True)
    stage: Mapped[str] = mapped_column(Enum('QUEUED', 'VALIDATING', 'DEPLOYING', 'VERIFYING', 'SUCCEEDED', 'FAILED', name='mappo_target_stage'), nullable=False)
    started_at: Mapped[datetime.datetime] = mapped_column(DateTime(True), nullable=False)
    message: Mapped[str] = mapped_column(Text, nullable=False, server_default=text("''::text"))
    correlation_id: Mapped[str] = mapped_column(Text, nullable=False)
    portal_link: Mapped[str] = mapped_column(Text, nullable=False)
    ended_at: Mapped[Optional[datetime.datetime]] = mapped_column(DateTime(True))
    error_code: Mapped[Optional[str]] = mapped_column(Text)
    error_message: Mapped[Optional[str]] = mapped_column(Text)
    error_details: Mapped[Optional[dict]] = mapped_column(JSONB)

    target_execution_records: Mapped['TargetExecutionRecords'] = relationship('TargetExecutionRecords', back_populates='target_stage_records')
