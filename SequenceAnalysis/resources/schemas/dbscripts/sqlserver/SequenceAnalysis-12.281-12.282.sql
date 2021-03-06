ALTER TABLE sequenceanalysis.sequence_readsets ADD status VARCHAR(200);
ALTER TABLE sequenceanalysis.saved_analyses ADD taskid VARCHAR(1000);

CREATE TABLE sequenceanalysis.outputfiles (
  rowid int identity(1,1),
  name varchar(1000),
  description varchar(4000),
  dataid int,
  library_id int,
  readset int,
  analysis_id int,
  runid int,
  category varchar(200),
  intermediate bit,
  container entityid NOT NULL,
  createdby int,
  created datetime,
  modifiedby int,
  modified datetime,

  CONSTRAINT PK_outputfiles PRIMARY KEY (rowid)
);

alter table sequenceanalysis.reference_library_members add start int;
alter table sequenceanalysis.reference_library_members add stop int;