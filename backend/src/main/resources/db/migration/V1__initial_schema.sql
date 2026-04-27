--
-- PostgreSQL database dump
--

-- Dumped from database version 16.13
-- Dumped by pg_dump version 16.9 (Homebrew)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: mappo_arm_deployment_mode; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.mappo_arm_deployment_mode AS ENUM (
    'incremental',
    'complete'
);


--
-- Name: mappo_deployment_scope; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.mappo_deployment_scope AS ENUM (
    'resource_group',
    'subscription'
);


--
-- Name: mappo_forwarder_log_level; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.mappo_forwarder_log_level AS ENUM (
    'info',
    'warning',
    'error'
);


--
-- Name: mappo_health_status; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.mappo_health_status AS ENUM (
    'registered',
    'healthy',
    'degraded'
);


--
-- Name: mappo_marketplace_event_status; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.mappo_marketplace_event_status AS ENUM (
    'applied',
    'duplicate',
    'rejected'
);


--
-- Name: mappo_marketplace_event_type; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.mappo_marketplace_event_type AS ENUM (
    'subscription_purchased',
    'subscription_suspended',
    'subscription_deleted',
    'unknown'
);


--
-- Name: mappo_project_access_strategy; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.mappo_project_access_strategy AS ENUM (
    'simulator',
    'azure_workload_rbac',
    'lighthouse_delegated_access'
);


--
-- Name: mappo_project_deployment_driver; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.mappo_project_deployment_driver AS ENUM (
    'azure_deployment_stack',
    'azure_template_spec',
    'pipeline_trigger'
);


--
-- Name: mappo_project_release_artifact_source; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.mappo_project_release_artifact_source AS ENUM (
    'blob_arm_template',
    'template_spec_resource',
    'external_deployment_inputs'
);


--
-- Name: mappo_project_runtime_health_provider; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.mappo_project_runtime_health_provider AS ENUM (
    'azure_container_app_http',
    'http_endpoint'
);


--
-- Name: mappo_registry_auth_mode; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.mappo_registry_auth_mode AS ENUM (
    'none',
    'shared_service_principal_secret',
    'customer_managed_secret'
);


--
-- Name: mappo_release_ingest_provider; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.mappo_release_ingest_provider AS ENUM (
    'github',
    'azure_devops'
);


--
-- Name: mappo_release_source_type; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.mappo_release_source_type AS ENUM (
    'template_spec',
    'bicep',
    'deployment_stack',
    'external_deployment_inputs'
);


--
-- Name: mappo_release_webhook_status; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.mappo_release_webhook_status AS ENUM (
    'applied',
    'skipped',
    'failed'
);


--
-- Name: mappo_run_status; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.mappo_run_status AS ENUM (
    'running',
    'succeeded',
    'failed',
    'partial',
    'halted'
);


--
-- Name: mappo_runtime_probe_status; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.mappo_runtime_probe_status AS ENUM (
    'unknown',
    'healthy',
    'unhealthy',
    'unreachable'
);


--
-- Name: mappo_simulated_failure_mode; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.mappo_simulated_failure_mode AS ENUM (
    'none',
    'validate_once',
    'deploy_once',
    'verify_once'
);


--
-- Name: mappo_strategy_mode; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.mappo_strategy_mode AS ENUM (
    'all_at_once',
    'waves'
);


--
-- Name: mappo_target_stage; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.mappo_target_stage AS ENUM (
    'QUEUED',
    'VALIDATING',
    'DEPLOYING',
    'VERIFYING',
    'SUCCEEDED',
    'FAILED'
);


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: forwarder_logs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.forwarder_logs (
    id character varying(128) NOT NULL,
    level public.mappo_forwarder_log_level NOT NULL,
    message text NOT NULL,
    event_id character varying(128),
    event_type public.mappo_marketplace_event_type,
    target_id character varying(128),
    tenant_id uuid,
    subscription_id uuid,
    function_app_name character varying(255),
    forwarder_request_id character varying(128),
    backend_status_code integer,
    detail_text text,
    backend_response_body text,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: marketplace_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.marketplace_events (
    id character varying(128) NOT NULL,
    event_type public.mappo_marketplace_event_type NOT NULL,
    status public.mappo_marketplace_event_status NOT NULL,
    message text NOT NULL,
    target_id character varying(128),
    tenant_id uuid NOT NULL,
    subscription_id uuid NOT NULL,
    display_name character varying(255),
    customer_name character varying(255),
    managed_application_id character varying(2048),
    managed_resource_group_id character varying(2048),
    container_app_resource_id character varying(2048),
    container_app_name character varying(255),
    target_group character varying(64),
    region character varying(64),
    environment character varying(64),
    tier character varying(64),
    last_deployed_release character varying(128),
    health_status public.mappo_health_status,
    registration_source character varying(64),
    marketplace_payload_id character varying(128),
    created_at timestamp with time zone NOT NULL,
    processed_at timestamp with time zone,
    project_id character varying(128)
);


--
-- Name: project_configuration_audit_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.project_configuration_audit_events (
    id character varying(128) NOT NULL,
    project_id character varying(128) NOT NULL,
    action character varying(32) NOT NULL,
    actor character varying(128),
    change_summary text NOT NULL,
    before_snapshot jsonb,
    after_snapshot jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: projects; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.projects (
    id character varying(128) NOT NULL,
    name character varying(255) NOT NULL,
    access_strategy public.mappo_project_access_strategy NOT NULL,
    deployment_driver public.mappo_project_deployment_driver NOT NULL,
    release_artifact_source public.mappo_project_release_artifact_source NOT NULL,
    runtime_health_provider public.mappo_project_runtime_health_provider NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    access_strategy_config jsonb DEFAULT '{}'::jsonb NOT NULL,
    deployment_driver_config jsonb DEFAULT '{}'::jsonb NOT NULL,
    release_artifact_source_config jsonb DEFAULT '{}'::jsonb NOT NULL,
    runtime_health_provider_config jsonb DEFAULT '{}'::jsonb NOT NULL,
    release_ingest_endpoint_id bigint,
    provider_connection_id bigint,
    theme_key character varying(64)
);


--
-- Name: provider_connection_ado_projects; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.provider_connection_ado_projects (
    connection_id bigint NOT NULL,
    project_id character varying(255) NOT NULL,
    project_name character varying(255) NOT NULL,
    web_url character varying(1024),
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: provider_connections; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.provider_connections (
    id bigint GENERATED BY DEFAULT AS IDENTITY NOT NULL,
    name character varying(255) NOT NULL,
    provider public.mappo_release_ingest_provider NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    organization_filter character varying(255),
    personal_access_token_ref character varying(255),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: release_external_input_entries; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.release_external_input_entries (
    release_id character varying(128) NOT NULL,
    input_key character varying(128) NOT NULL,
    input_value character varying(4096) NOT NULL
);


--
-- Name: release_ingest_endpoints; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.release_ingest_endpoints (
    id bigint GENERATED BY DEFAULT AS IDENTITY NOT NULL,
    name character varying(255) NOT NULL,
    provider public.mappo_release_ingest_provider NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    secret_ref character varying(255),
    repo_filter character varying(255),
    branch_filter character varying(255),
    pipeline_id_filter character varying(128),
    manifest_path character varying(512),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: release_parameter_defaults; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.release_parameter_defaults (
    release_id character varying(128) NOT NULL,
    param_key character varying(128) NOT NULL,
    param_value character varying(2048) NOT NULL
);


--
-- Name: release_verification_hints; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.release_verification_hints (
    release_id character varying(128) NOT NULL,
    "position" integer NOT NULL,
    hint text NOT NULL
);


--
-- Name: release_webhook_deliveries; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.release_webhook_deliveries (
    id character varying(128) NOT NULL,
    external_delivery_id character varying(128),
    event_type character varying(64) NOT NULL,
    repo character varying(255),
    ref character varying(255),
    manifest_path character varying(512),
    status public.mappo_release_webhook_status NOT NULL,
    message text NOT NULL,
    changed_paths_text text,
    manifest_release_count integer DEFAULT 0 NOT NULL,
    created_count integer DEFAULT 0 NOT NULL,
    skipped_count integer DEFAULT 0 NOT NULL,
    ignored_count integer DEFAULT 0 NOT NULL,
    created_release_ids_text text,
    received_at timestamp with time zone DEFAULT now() NOT NULL,
    project_ids_text text
);


--
-- Name: releases; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.releases (
    id character varying(128) NOT NULL,
    source_ref character varying(2048) NOT NULL,
    source_version character varying(128) NOT NULL,
    source_type public.mappo_release_source_type DEFAULT 'template_spec'::public.mappo_release_source_type NOT NULL,
    source_version_ref character varying(2048),
    deployment_scope public.mappo_deployment_scope DEFAULT 'resource_group'::public.mappo_deployment_scope NOT NULL,
    arm_deployment_mode public.mappo_arm_deployment_mode DEFAULT 'incremental'::public.mappo_arm_deployment_mode NOT NULL,
    what_if_on_canary boolean DEFAULT false NOT NULL,
    verify_after_deploy boolean DEFAULT true NOT NULL,
    release_notes text DEFAULT ''::text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    project_id character varying(128) NOT NULL
);


--
-- Name: run_guardrail_warnings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.run_guardrail_warnings (
    run_id character varying(128) NOT NULL,
    "position" integer NOT NULL,
    warning text NOT NULL
);


--
-- Name: run_targets; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.run_targets (
    run_id character varying(128) NOT NULL,
    "position" integer NOT NULL,
    target_id character varying(128) NOT NULL
);


--
-- Name: run_wave_order; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.run_wave_order (
    run_id character varying(128) NOT NULL,
    "position" integer NOT NULL,
    wave_value character varying(128) NOT NULL
);


--
-- Name: runs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.runs (
    id character varying(128) NOT NULL,
    release_id character varying(128) NOT NULL,
    execution_source_type public.mappo_release_source_type DEFAULT 'template_spec'::public.mappo_release_source_type NOT NULL,
    strategy_mode public.mappo_strategy_mode NOT NULL,
    wave_tag character varying(64) NOT NULL,
    concurrency integer NOT NULL,
    subscription_concurrency integer DEFAULT 1 NOT NULL,
    stop_policy_max_failure_count integer,
    stop_policy_max_failure_rate double precision,
    status public.mappo_run_status NOT NULL,
    halt_reason text,
    created_at timestamp with time zone NOT NULL,
    started_at timestamp with time zone,
    ended_at timestamp with time zone,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    project_id character varying(128) NOT NULL
);


--
-- Name: secret_references; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.secret_references (
    id bigint GENERATED BY DEFAULT AS IDENTITY NOT NULL,
    name character varying(255) NOT NULL,
    provider character varying(64) NOT NULL,
    usage character varying(64) NOT NULL,
    mode character varying(64) NOT NULL,
    backend_ref character varying(255) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: target_execution_config_entries; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.target_execution_config_entries (
    target_id character varying(128) NOT NULL,
    config_key character varying(128) NOT NULL,
    config_value character varying(2048) NOT NULL
);


--
-- Name: target_execution_records; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.target_execution_records (
    run_id character varying(128) NOT NULL,
    target_id character varying(128) NOT NULL,
    subscription_id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    status public.mappo_target_stage NOT NULL,
    attempt integer DEFAULT 0 NOT NULL,
    updated_at timestamp with time zone NOT NULL
);


--
-- Name: target_external_execution_handles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.target_external_execution_handles (
    run_id character varying(128) NOT NULL,
    target_id character varying(128) NOT NULL,
    provider character varying(64) NOT NULL,
    execution_id character varying(255),
    execution_name character varying(255),
    execution_status character varying(64),
    execution_url character varying(2048),
    logs_url character varying(2048),
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: target_log_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.target_log_events (
    run_id character varying(128) NOT NULL,
    target_id character varying(128) NOT NULL,
    "position" integer NOT NULL,
    event_timestamp timestamp with time zone NOT NULL,
    level public.mappo_forwarder_log_level NOT NULL,
    stage public.mappo_target_stage NOT NULL,
    message text NOT NULL,
    correlation_id character varying(128) NOT NULL
);


--
-- Name: target_registrations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.target_registrations (
    target_id character varying(128) NOT NULL,
    display_name character varying(255) NOT NULL,
    customer_name character varying(255),
    managed_application_id character varying(2048),
    managed_resource_group_id character varying(2048),
    container_app_resource_id character varying(2048),
    container_app_name character varying(255),
    registration_source character varying(64),
    last_event_id character varying(128),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deployment_stack_name character varying(128),
    registry_auth_mode public.mappo_registry_auth_mode,
    registry_server character varying(255),
    registry_username character varying(255),
    registry_password_secret_name character varying(255)
);


--
-- Name: target_runtime_probes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.target_runtime_probes (
    target_id character varying(128) NOT NULL,
    runtime_status public.mappo_runtime_probe_status NOT NULL,
    checked_at timestamp with time zone NOT NULL,
    endpoint_url character varying(2048),
    http_status_code integer,
    summary text DEFAULT ''::text NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: target_stage_records; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.target_stage_records (
    run_id character varying(128) NOT NULL,
    target_id character varying(128) NOT NULL,
    "position" integer NOT NULL,
    stage public.mappo_target_stage NOT NULL,
    started_at timestamp with time zone NOT NULL,
    ended_at timestamp with time zone,
    message text DEFAULT ''::text NOT NULL,
    error_code character varying(128),
    error_message text,
    error_status_code integer,
    error_detail_text text,
    error_desired_image character varying(2048),
    azure_error_code character varying(128),
    azure_error_message text,
    azure_request_id character varying(128),
    azure_arm_service_request_id character varying(128),
    azure_correlation_id character varying(128),
    azure_deployment_name character varying(128),
    azure_operation_id character varying(128),
    azure_resource_id character varying(2048),
    correlation_id character varying(128) NOT NULL,
    portal_link character varying(2048) NOT NULL
);


--
-- Name: target_tags; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.target_tags (
    target_id character varying(128) NOT NULL,
    tag_key character varying(64) NOT NULL,
    tag_value character varying(256) NOT NULL
);


--
-- Name: targets; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.targets (
    id character varying(128) NOT NULL,
    tenant_id uuid NOT NULL,
    subscription_id uuid NOT NULL,
    last_deployed_release character varying(128) NOT NULL,
    health_status public.mappo_health_status NOT NULL,
    last_check_in_at timestamp with time zone NOT NULL,
    simulated_failure_mode public.mappo_simulated_failure_mode DEFAULT 'none'::public.mappo_simulated_failure_mode NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    project_id character varying(128) NOT NULL
);


--
-- Name: forwarder_logs forwarder_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forwarder_logs
    ADD CONSTRAINT forwarder_logs_pkey PRIMARY KEY (id);


--
-- Name: marketplace_events marketplace_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.marketplace_events
    ADD CONSTRAINT marketplace_events_pkey PRIMARY KEY (id);


--
-- Name: project_configuration_audit_events project_configuration_audit_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_configuration_audit_events
    ADD CONSTRAINT project_configuration_audit_events_pkey PRIMARY KEY (id);


--
-- Name: projects projects_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projects
    ADD CONSTRAINT projects_name_key UNIQUE (name);


--
-- Name: projects projects_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projects
    ADD CONSTRAINT projects_pkey PRIMARY KEY (id);


--
-- Name: provider_connection_ado_projects provider_connection_ado_projects_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.provider_connection_ado_projects
    ADD CONSTRAINT provider_connection_ado_projects_pkey PRIMARY KEY (connection_id, project_id);


--
-- Name: provider_connections provider_connections_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.provider_connections
    ADD CONSTRAINT provider_connections_name_key UNIQUE (name);


--
-- Name: provider_connections provider_connections_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.provider_connections
    ADD CONSTRAINT provider_connections_pkey PRIMARY KEY (id);


--
-- Name: release_external_input_entries release_external_input_entries_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.release_external_input_entries
    ADD CONSTRAINT release_external_input_entries_pkey PRIMARY KEY (release_id, input_key);


--
-- Name: release_ingest_endpoints release_ingest_endpoints_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.release_ingest_endpoints
    ADD CONSTRAINT release_ingest_endpoints_name_key UNIQUE (name);


--
-- Name: release_ingest_endpoints release_ingest_endpoints_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.release_ingest_endpoints
    ADD CONSTRAINT release_ingest_endpoints_pkey PRIMARY KEY (id);


--
-- Name: release_parameter_defaults release_parameter_defaults_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.release_parameter_defaults
    ADD CONSTRAINT release_parameter_defaults_pkey PRIMARY KEY (release_id, param_key);


--
-- Name: release_verification_hints release_verification_hints_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.release_verification_hints
    ADD CONSTRAINT release_verification_hints_pkey PRIMARY KEY (release_id, "position");


--
-- Name: release_webhook_deliveries release_webhook_deliveries_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.release_webhook_deliveries
    ADD CONSTRAINT release_webhook_deliveries_pkey PRIMARY KEY (id);


--
-- Name: releases releases_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.releases
    ADD CONSTRAINT releases_pkey PRIMARY KEY (id);


--
-- Name: run_guardrail_warnings run_guardrail_warnings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.run_guardrail_warnings
    ADD CONSTRAINT run_guardrail_warnings_pkey PRIMARY KEY (run_id, "position");


--
-- Name: run_targets run_targets_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.run_targets
    ADD CONSTRAINT run_targets_pkey PRIMARY KEY (run_id, "position");


--
-- Name: run_wave_order run_wave_order_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.run_wave_order
    ADD CONSTRAINT run_wave_order_pkey PRIMARY KEY (run_id, "position");


--
-- Name: runs runs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.runs
    ADD CONSTRAINT runs_pkey PRIMARY KEY (id);


--
-- Name: secret_references secret_references_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.secret_references
    ADD CONSTRAINT secret_references_name_key UNIQUE (name);


--
-- Name: secret_references secret_references_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.secret_references
    ADD CONSTRAINT secret_references_pkey PRIMARY KEY (id);


--
-- Name: target_execution_config_entries target_execution_config_entries_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.target_execution_config_entries
    ADD CONSTRAINT target_execution_config_entries_pkey PRIMARY KEY (target_id, config_key);


--
-- Name: target_execution_records target_execution_records_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.target_execution_records
    ADD CONSTRAINT target_execution_records_pkey PRIMARY KEY (run_id, target_id);


--
-- Name: target_external_execution_handles target_external_execution_handles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.target_external_execution_handles
    ADD CONSTRAINT target_external_execution_handles_pkey PRIMARY KEY (run_id, target_id);


--
-- Name: target_log_events target_log_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.target_log_events
    ADD CONSTRAINT target_log_events_pkey PRIMARY KEY (run_id, target_id, "position");


--
-- Name: target_registrations target_registrations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.target_registrations
    ADD CONSTRAINT target_registrations_pkey PRIMARY KEY (target_id);


--
-- Name: target_runtime_probes target_runtime_probes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.target_runtime_probes
    ADD CONSTRAINT target_runtime_probes_pkey PRIMARY KEY (target_id);


--
-- Name: target_stage_records target_stage_records_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.target_stage_records
    ADD CONSTRAINT target_stage_records_pkey PRIMARY KEY (run_id, target_id, "position");


--
-- Name: target_tags target_tags_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.target_tags
    ADD CONSTRAINT target_tags_pkey PRIMARY KEY (target_id, tag_key);


--
-- Name: targets targets_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.targets
    ADD CONSTRAINT targets_pkey PRIMARY KEY (id);


--
-- Name: targets uq_targets_project_tenant_subscription; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.targets
    ADD CONSTRAINT uq_targets_project_tenant_subscription UNIQUE (project_id, tenant_id, subscription_id);


--
-- Name: idx_forwarder_logs_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_forwarder_logs_created_at ON public.forwarder_logs USING btree (created_at DESC);


--
-- Name: idx_forwarder_logs_level_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_forwarder_logs_level_created_at ON public.forwarder_logs USING btree (level, created_at DESC);


--
-- Name: idx_marketplace_events_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_marketplace_events_created_at ON public.marketplace_events USING btree (created_at DESC);


--
-- Name: idx_marketplace_events_project_id_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_marketplace_events_project_id_created_at ON public.marketplace_events USING btree (project_id, created_at DESC);


--
-- Name: idx_marketplace_events_status_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_marketplace_events_status_created_at ON public.marketplace_events USING btree (status, created_at DESC);


--
-- Name: idx_project_configuration_audit_events_project_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_project_configuration_audit_events_project_created_at ON public.project_configuration_audit_events USING btree (project_id, created_at DESC);


--
-- Name: idx_projects_provider_connection_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_projects_provider_connection_id ON public.projects USING btree (provider_connection_id);


--
-- Name: idx_projects_release_ingest_endpoint_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_projects_release_ingest_endpoint_id ON public.projects USING btree (release_ingest_endpoint_id);


--
-- Name: idx_projects_theme_key; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_projects_theme_key ON public.projects USING btree (theme_key);


--
-- Name: idx_provider_connection_ado_projects_connection_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_provider_connection_ado_projects_connection_id ON public.provider_connection_ado_projects USING btree (connection_id);


--
-- Name: idx_provider_connections_provider; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_provider_connections_provider ON public.provider_connections USING btree (provider);


--
-- Name: idx_release_ingest_endpoints_provider; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_release_ingest_endpoints_provider ON public.release_ingest_endpoints USING btree (provider);


--
-- Name: idx_release_webhook_deliveries_external_delivery_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_release_webhook_deliveries_external_delivery_id ON public.release_webhook_deliveries USING btree (external_delivery_id);


--
-- Name: idx_release_webhook_deliveries_received_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_release_webhook_deliveries_received_at ON public.release_webhook_deliveries USING btree (received_at DESC);


--
-- Name: idx_release_webhook_deliveries_status_received_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_release_webhook_deliveries_status_received_at ON public.release_webhook_deliveries USING btree (status, received_at DESC);


--
-- Name: idx_releases_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_releases_created_at ON public.releases USING btree (created_at DESC);


--
-- Name: idx_releases_project_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_releases_project_id ON public.releases USING btree (project_id);


--
-- Name: idx_run_targets_target_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_run_targets_target_id ON public.run_targets USING btree (target_id);


--
-- Name: idx_runs_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_runs_created_at ON public.runs USING btree (created_at DESC);


--
-- Name: idx_runs_project_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_runs_project_id ON public.runs USING btree (project_id);


--
-- Name: idx_runs_status_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_runs_status_created_at ON public.runs USING btree (status, created_at DESC);


--
-- Name: idx_runs_updated_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_runs_updated_at ON public.runs USING btree (updated_at DESC);


--
-- Name: idx_secret_references_provider_usage; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_secret_references_provider_usage ON public.secret_references USING btree (provider, usage);


--
-- Name: idx_target_execution_config_entries_target_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_target_execution_config_entries_target_id ON public.target_execution_config_entries USING btree (target_id);


--
-- Name: idx_target_execution_records_run_status_updated_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_target_execution_records_run_status_updated_at ON public.target_execution_records USING btree (run_id, status, updated_at DESC);


--
-- Name: idx_target_external_execution_handles_updated_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_target_external_execution_handles_updated_at ON public.target_external_execution_handles USING btree (updated_at DESC);


--
-- Name: idx_target_registrations_updated_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_target_registrations_updated_at ON public.target_registrations USING btree (updated_at DESC);


--
-- Name: idx_target_runtime_probes_status_checked_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_target_runtime_probes_status_checked_at ON public.target_runtime_probes USING btree (runtime_status, checked_at DESC);


--
-- Name: idx_target_tags_key_value; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_target_tags_key_value ON public.target_tags USING btree (tag_key, tag_value);


--
-- Name: idx_targets_project_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_targets_project_id ON public.targets USING btree (project_id);


--
-- Name: releases fk_releases_project_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.releases
    ADD CONSTRAINT fk_releases_project_id FOREIGN KEY (project_id) REFERENCES public.projects(id);


--
-- Name: runs fk_runs_project_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.runs
    ADD CONSTRAINT fk_runs_project_id FOREIGN KEY (project_id) REFERENCES public.projects(id);


--
-- Name: targets fk_targets_project_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.targets
    ADD CONSTRAINT fk_targets_project_id FOREIGN KEY (project_id) REFERENCES public.projects(id);


--
-- Name: project_configuration_audit_events project_configuration_audit_events_project_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_configuration_audit_events
    ADD CONSTRAINT project_configuration_audit_events_project_id_fkey FOREIGN KEY (project_id) REFERENCES public.projects(id) ON DELETE CASCADE;


--
-- Name: projects projects_provider_connection_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projects
    ADD CONSTRAINT projects_provider_connection_id_fkey FOREIGN KEY (provider_connection_id) REFERENCES public.provider_connections(id) ON DELETE SET NULL;


--
-- Name: projects projects_release_ingest_endpoint_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projects
    ADD CONSTRAINT projects_release_ingest_endpoint_id_fkey FOREIGN KEY (release_ingest_endpoint_id) REFERENCES public.release_ingest_endpoints(id) ON DELETE SET NULL;


--
-- Name: provider_connection_ado_projects provider_connection_ado_projects_connection_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.provider_connection_ado_projects
    ADD CONSTRAINT provider_connection_ado_projects_connection_id_fkey FOREIGN KEY (connection_id) REFERENCES public.provider_connections(id) ON DELETE CASCADE;


--
-- Name: release_external_input_entries release_external_input_entries_release_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.release_external_input_entries
    ADD CONSTRAINT release_external_input_entries_release_id_fkey FOREIGN KEY (release_id) REFERENCES public.releases(id) ON DELETE CASCADE;


--
-- Name: release_parameter_defaults release_parameter_defaults_release_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.release_parameter_defaults
    ADD CONSTRAINT release_parameter_defaults_release_id_fkey FOREIGN KEY (release_id) REFERENCES public.releases(id) ON DELETE CASCADE;


--
-- Name: release_verification_hints release_verification_hints_release_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.release_verification_hints
    ADD CONSTRAINT release_verification_hints_release_id_fkey FOREIGN KEY (release_id) REFERENCES public.releases(id) ON DELETE CASCADE;


--
-- Name: run_guardrail_warnings run_guardrail_warnings_run_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.run_guardrail_warnings
    ADD CONSTRAINT run_guardrail_warnings_run_id_fkey FOREIGN KEY (run_id) REFERENCES public.runs(id) ON DELETE CASCADE;


--
-- Name: run_targets run_targets_run_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.run_targets
    ADD CONSTRAINT run_targets_run_id_fkey FOREIGN KEY (run_id) REFERENCES public.runs(id) ON DELETE CASCADE;


--
-- Name: run_wave_order run_wave_order_run_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.run_wave_order
    ADD CONSTRAINT run_wave_order_run_id_fkey FOREIGN KEY (run_id) REFERENCES public.runs(id) ON DELETE CASCADE;


--
-- Name: target_execution_config_entries target_execution_config_entries_target_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.target_execution_config_entries
    ADD CONSTRAINT target_execution_config_entries_target_id_fkey FOREIGN KEY (target_id) REFERENCES public.targets(id) ON DELETE CASCADE;


--
-- Name: target_execution_records target_execution_records_run_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.target_execution_records
    ADD CONSTRAINT target_execution_records_run_id_fkey FOREIGN KEY (run_id) REFERENCES public.runs(id) ON DELETE CASCADE;


--
-- Name: target_execution_records target_execution_records_target_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.target_execution_records
    ADD CONSTRAINT target_execution_records_target_id_fkey FOREIGN KEY (target_id) REFERENCES public.targets(id) ON DELETE CASCADE;


--
-- Name: target_external_execution_handles target_external_execution_handles_run_id_target_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.target_external_execution_handles
    ADD CONSTRAINT target_external_execution_handles_run_id_target_id_fkey FOREIGN KEY (run_id, target_id) REFERENCES public.target_execution_records(run_id, target_id) ON DELETE CASCADE;


--
-- Name: target_log_events target_log_events_run_id_target_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.target_log_events
    ADD CONSTRAINT target_log_events_run_id_target_id_fkey FOREIGN KEY (run_id, target_id) REFERENCES public.target_execution_records(run_id, target_id) ON DELETE CASCADE;


--
-- Name: target_registrations target_registrations_target_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.target_registrations
    ADD CONSTRAINT target_registrations_target_id_fkey FOREIGN KEY (target_id) REFERENCES public.targets(id) ON DELETE CASCADE;


--
-- Name: target_runtime_probes target_runtime_probes_target_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.target_runtime_probes
    ADD CONSTRAINT target_runtime_probes_target_id_fkey FOREIGN KEY (target_id) REFERENCES public.targets(id) ON DELETE CASCADE;


--
-- Name: target_stage_records target_stage_records_run_id_target_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.target_stage_records
    ADD CONSTRAINT target_stage_records_run_id_target_id_fkey FOREIGN KEY (run_id, target_id) REFERENCES public.target_execution_records(run_id, target_id) ON DELETE CASCADE;


--
-- Name: target_tags target_tags_target_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.target_tags
    ADD CONSTRAINT target_tags_target_id_fkey FOREIGN KEY (target_id) REFERENCES public.targets(id) ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--
