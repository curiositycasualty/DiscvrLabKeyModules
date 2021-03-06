/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

DROP TABLE IF EXISTS sequenceanalysis.instruments;
CREATE TABLE sequenceanalysis.instruments (
  rowId SERIAL NOT NULL,
  displayName varchar(200),
  identifier varchar(200),
  platform varchar(200),
  location varchar(200),

  CreatedBy USERID,
  Created TIMESTAMP,
  ModifiedBy USERID,
  Modified TIMESTAMP,

  CONSTRAINT PK_instruments PRIMARY KEY (rowId)
);

DROP TABLE IF EXISTS sequenceanalysis.instrument_runs;
CREATE TABLE sequenceanalysis.instrument_runs (
  rowId SERIAL NOT NULL,
  runDate timestamp,
  name varchar(200),
  instrumentId integer,
  comment varchar(4000),

  Container ENTITYID NOT NULL,
  CreatedBy USERID,
  Created TIMESTAMP,
  ModifiedBy USERID,
  Modified TIMESTAMP,

  CONSTRAINT PK_instrument_runs PRIMARY KEY (rowId)
);

DROP TABLE IF EXISTS sequenceanalysis.quality_metrics;
CREATE TABLE sequenceanalysis.quality_metrics (
  rowid SERIAL NOT NULL,
  runId integer,
  dataId integer,
  metricName varchar(200),
  metricValue float,
  qualValue varchar(100),
  comment varchar(4000),

  Container ENTITYID NOT NULL,
  CreatedBy USERID,
  Created TIMESTAMP,
  ModifiedBy USERID,
  Modified TIMESTAMP,

  CONSTRAINT PK_quality_metrics PRIMARY KEY (rowId)
);

DROP TABLE IF EXISTS sequenceanalysis.quality_metrics_types;
CREATE TABLE sequenceanalysis.quality_metrics_types (
  type varchar(200),
  CONSTRAINT PK_quality_metrics_types PRIMARY KEY (type)
);

INSERT INTO sequenceanalysis.quality_metrics_types (type) VALUES ('Total Sequences');
INSERT INTO sequenceanalysis.quality_metrics_types (type) VALUES ('Filtered Sequences');
INSERT INTO sequenceanalysis.quality_metrics_types (type) VALUES ('Avg Sequence Length');
INSERT INTO sequenceanalysis.quality_metrics_types (type) VALUES ('Min Sequence Length');
INSERT INTO sequenceanalysis.quality_metrics_types (type) VALUES ('Max Sequence Length');
INSERT INTO sequenceanalysis.quality_metrics_types (type) VALUES ('%GC');

ALTER TABLE sequenceanalysis.sequence_readsets DROP COLUMN machine_run_id;
ALTER TABLE sequenceanalysis.sequence_readsets ADD COLUMN instrument_run_id integer;
ALTER TABLE sequenceanalysis.sequence_readsets ADD COLUMN runid integer;