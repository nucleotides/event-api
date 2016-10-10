--;;
--;; Input data file fully denormalised
--;;
CREATE MATERIALIZED VIEW input_data_file_expanded_fields AS
SELECT
file_instance.id                               AS file_instance_id,
file_type.id                                   AS file_type_id,
input_data_file_set.id                         AS input_data_file_set_id,
input_data_file.id                             AS input_data_file_id,
biological_source.id                           AS biological_source_id,
input_data_file_set.platform_type_id,
input_data_file_set.protocol_type_id,
input_data_file_set.material_type_id,
input_data_file_set.extraction_method_type_id,
input_data_file_set.run_mode_type_id,
file_instance.created_at                       AS file_instance_created_at,
input_data_file_set.created_at                 AS input_file_set_created_at,
input_data_file.created_at                     AS input_file_created_at,
file_type.name                                 AS file_type,
platform_type.name                             AS platform,
protocol_type.name                             AS protocol,
material_type.name                             AS material,
extraction_method_type.name                    AS extraction_method,
run_mode_type.name                             AS run_mode,
source_type.name                               AS biological_source_type,
biological_source.name                         AS biological_source_name,
input_data_file_set.name                       AS input_file_set_name,
input_data_file_set.active                     AS input_file_set_active,
input_data_file.active                         AS input_file_active,
biological_source.active                       AS biological_source_active,
file_instance.sha256,
file_instance.url
FROM input_data_file
LEFT JOIN file_instance          ON file_instance.id = input_data_file.file_instance_id
LEFT JOIN input_data_file_set    ON input_data_file.input_data_file_set_id = input_data_file_set.id
LEFT JOIN biological_source      ON input_data_file_set.biological_source_id = biological_source.id
LEFT JOIN source_type            ON biological_source.source_type_id = source_type.id
LEFT JOIN file_type              ON file_type.id = file_instance.file_type_id
LEFT JOIN platform_type          ON platform_type.id = input_data_file_set.platform_type_id
LEFT JOIN protocol_type          ON protocol_type.id = input_data_file_set.protocol_type_id
LEFT JOIN material_type          ON material_type.id = input_data_file_set.material_type_id
LEFT JOIN extraction_method_type ON extraction_method_type.id = input_data_file_set.extraction_method_type_id
LEFT JOIN run_mode_type          ON run_mode_type.id = input_data_file_set.run_mode_type_id;
--;;
CREATE INDEX input_data_file_expanded_fields_file_instance_id  ON input_data_file_expanded_fields (file_instance_id);
--;;
CREATE INDEX input_data_file_expanded_fields_file_type_id  ON input_data_file_expanded_fields (file_type_id);
--;;
CREATE INDEX input_data_file_expanded_fields_input_data_file_set_id  ON input_data_file_expanded_fields (input_data_file_set_id);
--;;
CREATE INDEX input_data_file_expanded_fields_input_data_file_id  ON input_data_file_expanded_fields (input_data_file_id);
--;;
CREATE INDEX input_data_file_expanded_fields_biological_source_id  ON input_data_file_expanded_fields (biological_source_id);
--;;
CREATE INDEX input_data_file_expanded_fields_platform_type_id  ON input_data_file_expanded_fields (platform_type_id);
--;;
CREATE INDEX input_data_file_expanded_fields_protocol_type_id  ON input_data_file_expanded_fields (protocol_type_id);
--;;
CREATE INDEX input_data_file_expanded_fields_material_type_id  ON input_data_file_expanded_fields (material_type_id);
--;;
CREATE INDEX input_data_file_expanded_fields_extraction_method_type_id  ON input_data_file_expanded_fields (extraction_method_type_id);
--;;
CREATE INDEX input_data_file_expanded_fields_run_mode_type_id  ON input_data_file_expanded_fields (run_mode_type_id);
--;;
--;; Materialised view of denormalised image data
--;;
CREATE MATERIALIZED VIEW image_expanded_fields AS
SELECT
image_type.id             AS image_type_id,
image_instance.id         AS image_instance_id,
image_version.id          AS image_version_id,
image_task.id             AS image_task_id,
image_type.created_at     AS image_type_created_at,
image_instance.created_at AS image_instance_created_at,
image_version.created_at  AS image_version_created_at,
image_task.created_at     AS image_task_created_at,
image_type.name           AS image_type_name,
image_instance.name       AS image_instance_name,
image_version.name        AS image_version_name,
image_version.sha256      AS image_version_sha256,
image_task.name           AS image_task_name
FROM image_type
INNER JOIN image_instance ON image_instance.image_type_id = image_type.id
INNER JOIN image_version  ON image_version.image_instance_id = image_instance.id
INNER JOIN image_task     ON image_task.image_version_id = image_version.id;
--;;
CREATE INDEX image_expanded_fields_image_type_idx  ON image_expanded_fields (image_type_id);
--;;
CREATE INDEX image_expanded_fields_image_instance_idx  ON image_expanded_fields (image_instance_id);
--;;
CREATE INDEX image_expanded_fields_image_version_idx  ON image_expanded_fields (image_version_id);
--;;
CREATE INDEX image_expanded_fields_image_task_idx  ON image_expanded_fields (image_task_id);
--;;
--;; Combination of image fields are unique
CREATE UNIQUE INDEX image_expanded_fields_unique_idx ON image_expanded_fields (image_type_id, image_instance_id, image_version_id, image_task_id);
--;;
--;; Updated function for populating all benchmark_instance and task
--;;
CREATE OR REPLACE FUNCTION populate_benchmark_instance () RETURNS void AS $$
BEGIN
REFRESH MATERIALIZED VIEW input_data_file_expanded_fields;
REFRESH MATERIALIZED VIEW image_expanded_fields;
INSERT INTO benchmark_instance(
	benchmark_type_id,
	product_image_instance_id,
	product_image_version_id,
	product_image_task_id,
	input_data_file_id,
	file_instance_id)
SELECT
benchmark_type.id,
images.image_instance_id,
images.image_version_id,
images.image_task_id,
inputs.input_data_file_id,
inputs.file_instance_id
FROM benchmark_type
LEFT JOIN benchmark_data                            ON benchmark_data.benchmark_type_id = benchmark_type.id
LEFT JOIN input_data_file_expanded_fields AS inputs ON inputs.input_data_file_set_id = benchmark_data.input_data_file_set_id
LEFT JOIN image_expanded_fields           AS images ON images.image_type_id = benchmark_type.product_image_type_id
ORDER BY benchmark_type.id,
	inputs.input_data_file_id,
	images.image_instance_id,
	images.image_task_id ASC
ON CONFLICT DO NOTHING;
END; $$
LANGUAGE PLPGSQL;
