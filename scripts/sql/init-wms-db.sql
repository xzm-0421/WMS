/*
  Template — generated at runtime by init-wms-db.ps1 from init-wms-db.config.json
  Placeholders: {{DATABASE}} {{APP_USER}} {{APP_PASSWORD}} {{COLLATION}}
*/
SET NOCOUNT ON;
GO

USE [master];
GO

IF DB_ID(N'{{DATABASE}}') IS NULL
BEGIN
    PRINT N'创建数据库 {{DATABASE}}...';
    CREATE DATABASE [{{DATABASE}}]
        COLLATE {{COLLATION}};
END
ELSE
    PRINT N'数据库 {{DATABASE}} 已存在，跳过创建。';
GO

ALTER DATABASE [{{DATABASE}}] SET RECOVERY SIMPLE;
GO

IF NOT EXISTS (SELECT 1 FROM sys.sql_logins WHERE name = N'{{APP_USER}}')
BEGIN
    PRINT N'创建登录 {{APP_USER}}...';
    CREATE LOGIN [{{APP_USER}}] WITH PASSWORD = N'{{APP_PASSWORD}}',
        CHECK_POLICY = OFF,
        CHECK_EXPIRATION = OFF,
        DEFAULT_DATABASE = [{{DATABASE}}];
END
ELSE
BEGIN
    PRINT N'登录 {{APP_USER}} 已存在，更新密码...';
    ALTER LOGIN [{{APP_USER}}] WITH PASSWORD = N'{{APP_PASSWORD}}';
END
GO

USE [{{DATABASE}}];
GO

IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = N'{{APP_USER}}')
BEGIN
    PRINT N'创建库用户 {{APP_USER}}...';
    CREATE USER [{{APP_USER}}] FOR LOGIN [{{APP_USER}}];
END
ELSE
    PRINT N'库用户 {{APP_USER}} 已存在，跳过创建。';
GO

ALTER ROLE [db_owner] ADD MEMBER [{{APP_USER}}];
GO

PRINT N'完成：数据库 {{DATABASE}} / 用户 {{APP_USER}} 已就绪。';
PRINT N'下一步：启动 wms-backend，Flyway 会自动执行 db/migration/sqlserver 脚本建表。';
GO
