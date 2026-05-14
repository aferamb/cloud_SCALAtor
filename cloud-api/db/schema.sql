IF OBJECT_ID('dbo.phase_output_items', 'U') IS NOT NULL
  DROP TABLE dbo.phase_output_items;
GO

IF OBJECT_ID('dbo.api_audit_events', 'U') IS NOT NULL
  DROP TABLE dbo.api_audit_events;
GO

IF OBJECT_ID('dbo.phase_runs', 'U') IS NOT NULL
  DROP TABLE dbo.phase_runs;
GO

CREATE TABLE dbo.phase_runs (
  id INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_phase_runs PRIMARY KEY,
  run_uuid UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_phase_runs_uuid DEFAULT NEWID(),
  user_name NVARCHAR(120) NOT NULL,
  phase_code NVARCHAR(20) NOT NULL,
  phase_name NVARCHAR(160) NOT NULL,
  executed_at DATETIMEOFFSET NOT NULL,
  dataset_path NVARCHAR(500) NULL,
  rows_read INT NULL,
  stored_rows INT NULL,
  discarded_rows INT NULL,
  missing_departure_delay INT NULL,
  missing_arrival_delay INT NULL,
  missing_weather_delay INT NULL,
  input_options_json NVARCHAR(MAX) NOT NULL,
  result_summary NVARCHAR(1000) NOT NULL,
  console_output NVARCHAR(MAX) NULL,
  item_count INT NOT NULL CONSTRAINT DF_phase_runs_item_count DEFAULT 0,
  inserted_at DATETIMEOFFSET NOT NULL CONSTRAINT DF_phase_runs_inserted_at DEFAULT SYSDATETIMEOFFSET(),
  client_ip NVARCHAR(80) NULL,
  user_agent NVARCHAR(500) NULL,
  source_app NVARCHAR(120) NULL,
  CONSTRAINT UQ_phase_runs_uuid UNIQUE (run_uuid),
  CONSTRAINT CK_phase_runs_input_options_json CHECK (ISJSON(input_options_json) = 1),
  CONSTRAINT CK_phase_runs_phase_code CHECK (phase_code IN ('PHASE_01', 'PHASE_02', 'PHASE_03', 'PHASE_04'))
);
GO

CREATE INDEX IX_phase_runs_executed_at ON dbo.phase_runs (executed_at DESC, id DESC);
GO

CREATE INDEX IX_phase_runs_user_name ON dbo.phase_runs (user_name);
GO

CREATE INDEX IX_phase_runs_phase_code ON dbo.phase_runs (phase_code);
GO

CREATE TABLE dbo.phase_output_items (
  id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_phase_output_items PRIMARY KEY,
  run_id INT NOT NULL,
  item_index INT NOT NULL,
  item_type NVARCHAR(60) NULL,
  flight_id INT NULL,
  tail_num NVARCHAR(40) NULL,
  airport_code NVARCHAR(12) NULL,
  airport_seq_id INT NULL,
  delay_kind NVARCHAR(40) NULL,
  delay_minutes INT NULL,
  reduction_column NVARCHAR(40) NULL,
  reduction_type NVARCHAR(40) NULL,
  reduction_value INT NULL,
  valid_count INT NULL,
  airport_kind NVARCHAR(40) NULL,
  airport_count INT NULL,
  bar_text NVARCHAR(100) NULL,
  raw_text NVARCHAR(1000) NULL,
  item_json NVARCHAR(MAX) NOT NULL,
  inserted_at DATETIMEOFFSET NOT NULL CONSTRAINT DF_phase_output_items_inserted_at DEFAULT SYSDATETIMEOFFSET(),
  CONSTRAINT FK_phase_output_items_phase_runs
    FOREIGN KEY (run_id) REFERENCES dbo.phase_runs(id) ON DELETE CASCADE,
  CONSTRAINT CK_phase_output_items_item_json CHECK (ISJSON(item_json) = 1)
);
GO

CREATE INDEX IX_phase_output_items_run_id ON dbo.phase_output_items (run_id, item_index);
GO

CREATE TABLE dbo.api_audit_events (
  id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_api_audit_events PRIMARY KEY,
  event_type NVARCHAR(80) NOT NULL,
  run_id INT NULL,
  method NVARCHAR(12) NULL,
  path NVARCHAR(300) NULL,
  status_code INT NULL,
  client_ip NVARCHAR(80) NULL,
  user_agent NVARCHAR(500) NULL,
  details_json NVARCHAR(MAX) NULL,
  created_at DATETIMEOFFSET NOT NULL CONSTRAINT DF_api_audit_events_created_at DEFAULT SYSDATETIMEOFFSET(),
  CONSTRAINT FK_api_audit_events_phase_runs
    FOREIGN KEY (run_id) REFERENCES dbo.phase_runs(id) ON DELETE SET NULL,
  CONSTRAINT CK_api_audit_events_details_json CHECK (details_json IS NULL OR ISJSON(details_json) = 1)
);
GO

CREATE INDEX IX_api_audit_events_created_at ON dbo.api_audit_events (created_at DESC, id DESC);
GO
