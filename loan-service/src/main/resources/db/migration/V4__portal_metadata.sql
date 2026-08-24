ALTER TABLE origination.application ADD COLUMN product_id varchar(60);
ALTER TABLE origination.application ADD COLUMN product_version integer;
CREATE INDEX application_queue_idx ON origination.application(tenant,state,created_at DESC,id DESC);
CREATE INDEX application_product_idx ON origination.application(tenant,product_id,created_at DESC);
