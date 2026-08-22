-- Operational metadata only; existing jobs retain their original references.
ALTER TABLE origination.durable_job ADD COLUMN correlation_id varchar(100);
