-- S0-03 清空后校验（三段都必须达到期望值）

-- 1) 引擎表应剩 0 张
SELECT COUNT(*) AS engine_left FROM information_schema.TABLES WHERE TABLE_SCHEMA IN ('scm_tenant_20250519_001','scm_manager') AND (TABLE_NAME LIKE 'ACT\_%' OR TABLE_NAME LIKE 'FLW\_%');

-- 2) 7 张运行时镜像表应全为 0 行
SELECT 'T.bpm_instance' AS tbl, COUNT(*) AS n FROM scm_tenant_20250519_001.`bpm_instance`;
SELECT 'T.bpm_instance_summary' AS tbl, COUNT(*) AS n FROM scm_tenant_20250519_001.`bpm_instance_summary`;
SELECT 'T.bpm_instance_process' AS tbl, COUNT(*) AS n FROM scm_tenant_20250519_001.`bpm_instance_process`;
SELECT 'T.bpm_instance_approve' AS tbl, COUNT(*) AS n FROM scm_tenant_20250519_001.`bpm_instance_approve`;
SELECT 'T.bpm_todo' AS tbl, COUNT(*) AS n FROM scm_tenant_20250519_001.`bpm_todo`;
SELECT 'T.bpm_cc' AS tbl, COUNT(*) AS n FROM scm_tenant_20250519_001.`bpm_cc`;
SELECT 'T.bpm_comment' AS tbl, COUNT(*) AS n FROM scm_tenant_20250519_001.`bpm_comment`;

-- 3) 12 张配置表行数必须与 S0-01 的 C 段完全一致
SELECT 'T.bpm_process_templates' AS tbl, COUNT(*) AS n FROM scm_tenant_20250519_001.`bpm_process_templates`;
SELECT 'T.bpm_forms' AS tbl, COUNT(*) AS n FROM scm_tenant_20250519_001.`bpm_forms`;
SELECT 'T.bpm_form_groups' AS tbl, COUNT(*) AS n FROM scm_tenant_20250519_001.`bpm_form_groups`;
SELECT 'T.bpm_users' AS tbl, COUNT(*) AS n FROM scm_tenant_20250519_001.`bpm_users`;
SELECT 'T.bpm_departments' AS tbl, COUNT(*) AS n FROM scm_tenant_20250519_001.`bpm_departments`;
SELECT 'T.bpm_roles' AS tbl, COUNT(*) AS n FROM scm_tenant_20250519_001.`bpm_roles`;
SELECT 'T.bpm_user_departments' AS tbl, COUNT(*) AS n FROM scm_tenant_20250519_001.`bpm_user_departments`;
SELECT 'T.bpm_user_roles' AS tbl, COUNT(*) AS n FROM scm_tenant_20250519_001.`bpm_user_roles`;
SELECT 'T.bpm_setting' AS tbl, COUNT(*) AS n FROM scm_tenant_20250519_001.`bpm_setting`;
SELECT 'T.bpm_process_templates_20250220' AS tbl, COUNT(*) AS n FROM scm_tenant_20250519_001.`bpm_process_templates_20250220`;
SELECT 'T.bpm_process_templates_copy1' AS tbl, COUNT(*) AS n FROM scm_tenant_20250519_001.`bpm_process_templates_copy1`;
SELECT 'T.bpm_users_py_backup' AS tbl, COUNT(*) AS n FROM scm_tenant_20250519_001.`bpm_users_py_backup`;

-- 4) 业务表 bpm_instance_id 应全为 0 行
SELECT 'T.b_ap' AS tbl, COUNT(*) AS n FROM scm_tenant_20250519_001.`b_ap` WHERE bpm_instance_id IS NOT NULL;
SELECT 'T.b_ap_refund' AS tbl, COUNT(*) AS n FROM scm_tenant_20250519_001.`b_ap_refund` WHERE bpm_instance_id IS NOT NULL;
SELECT 'T.b_ar' AS tbl, COUNT(*) AS n FROM scm_tenant_20250519_001.`b_ar` WHERE bpm_instance_id IS NOT NULL;
SELECT 'T.b_ar_refund' AS tbl, COUNT(*) AS n FROM scm_tenant_20250519_001.`b_ar_refund` WHERE bpm_instance_id IS NOT NULL;
SELECT 'T.b_in' AS tbl, COUNT(*) AS n FROM scm_tenant_20250519_001.`b_in` WHERE bpm_instance_id IS NOT NULL;
SELECT 'T.b_in_plan' AS tbl, COUNT(*) AS n FROM scm_tenant_20250519_001.`b_in_plan` WHERE bpm_instance_id IS NOT NULL;
SELECT 'T.b_out' AS tbl, COUNT(*) AS n FROM scm_tenant_20250519_001.`b_out` WHERE bpm_instance_id IS NOT NULL;
SELECT 'T.b_out_plan' AS tbl, COUNT(*) AS n FROM scm_tenant_20250519_001.`b_out_plan` WHERE bpm_instance_id IS NOT NULL;
SELECT 'T.b_po_cargo_right_transfer' AS tbl, COUNT(*) AS n FROM scm_tenant_20250519_001.`b_po_cargo_right_transfer` WHERE bpm_instance_id IS NOT NULL;
SELECT 'T.b_po_contract' AS tbl, COUNT(*) AS n FROM scm_tenant_20250519_001.`b_po_contract` WHERE bpm_instance_id IS NOT NULL;
SELECT 'T.b_po_order' AS tbl, COUNT(*) AS n FROM scm_tenant_20250519_001.`b_po_order` WHERE bpm_instance_id IS NOT NULL;
SELECT 'T.b_po_settlement' AS tbl, COUNT(*) AS n FROM scm_tenant_20250519_001.`b_po_settlement` WHERE bpm_instance_id IS NOT NULL;
SELECT 'T.b_project' AS tbl, COUNT(*) AS n FROM scm_tenant_20250519_001.`b_project` WHERE bpm_instance_id IS NOT NULL;
SELECT 'T.b_so_cargo_right_transfer' AS tbl, COUNT(*) AS n FROM scm_tenant_20250519_001.`b_so_cargo_right_transfer` WHERE bpm_instance_id IS NOT NULL;
SELECT 'T.b_so_contract' AS tbl, COUNT(*) AS n FROM scm_tenant_20250519_001.`b_so_contract` WHERE bpm_instance_id IS NOT NULL;
SELECT 'T.b_so_order' AS tbl, COUNT(*) AS n FROM scm_tenant_20250519_001.`b_so_order` WHERE bpm_instance_id IS NOT NULL;
SELECT 'T.b_so_order_backup_20250723' AS tbl, COUNT(*) AS n FROM scm_tenant_20250519_001.`b_so_order_backup_20250723` WHERE bpm_instance_id IS NOT NULL;
SELECT 'T.b_so_settlement' AS tbl, COUNT(*) AS n FROM scm_tenant_20250519_001.`b_so_settlement` WHERE bpm_instance_id IS NOT NULL;
SELECT 'T.m_customer' AS tbl, COUNT(*) AS n FROM scm_tenant_20250519_001.`m_customer` WHERE bpm_instance_id IS NOT NULL;
SELECT 'T.m_enterprise' AS tbl, COUNT(*) AS n FROM scm_tenant_20250519_001.`m_enterprise` WHERE bpm_instance_id IS NOT NULL;
