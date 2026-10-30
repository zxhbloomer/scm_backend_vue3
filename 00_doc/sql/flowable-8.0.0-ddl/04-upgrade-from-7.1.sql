-- Flowable 7.1.x → 8.0.0 增量升级脚本（从 flowable 8.0.0 官方 jar 原样提取，未改动一行）
-- 来源：flowable-engine-8.0.0.jar / flowable-engine-common-8.0.0.jar 的 org/flowable/**/db/upgrade/
-- 适用：act_ge_property.schema.version 在 7.1.x（7100/7101/7102）且未升级过的库
-- 执行前必须备份；执行后 act_ge_property.schema.version 应为 8.0.0.0

-- ==================== flowable.mysql.upgradestep.7101.to.7102.common.sql ====================
create index ACT_IDX_EVENT_SUBSCR_EXEC_ID on ACT_RU_EVENT_SUBSCR(EXECUTION_ID_);
create index ACT_IDX_EVENT_SUBSCR_PROC_ID on ACT_RU_EVENT_SUBSCR(PROC_INST_ID_);

update ACT_GE_PROPERTY set VALUE_ = '7.1.0.2' where NAME_ = 'common.schema.version';

-- ==================== flowable.mysql.upgradestep.7102.to.7200.engine.sql ====================
alter table ACT_RU_ACTINST add column COMPLETED_BY_ varchar(255);

update ACT_GE_PROPERTY set VALUE_ = '7.2.0.0' where NAME_ = 'schema.version';
-- ==================== flowable.mysql.upgradestep.7102.to.7200.history.sql ====================
alter table ACT_HI_ACTINST add column COMPLETED_BY_ varchar(255);

-- ==================== flowable.mysql.upgradestep.7201.to.7202.engine.sql ====================
create index ACT_IDX_BYTEAR_DEPL on ACT_GE_BYTEARRAY(DEPLOYMENT_ID_);

update ACT_GE_PROPERTY set VALUE_ = '7.2.0.2' where NAME_ = 'schema.version';

-- ==================== flowable.mysql.upgradestep.7202.to.8000.engine.sql ====================
update ACT_GE_PROPERTY set VALUE_ = '8.0.0.0' where NAME_ = 'schema.version';
-- ==================== flowable.mysql.upgradestep.7202.to.8000.history.sql ====================
alter table ACT_HI_PROCINST add column END_USER_ID_ varchar(255);
alter table ACT_HI_PROCINST add column STATE_ varchar(255);


