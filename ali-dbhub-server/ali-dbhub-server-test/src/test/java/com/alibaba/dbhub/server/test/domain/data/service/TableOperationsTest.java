package com.alibaba.dbhub.server.test.domain.data.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import com.alibaba.dbhub.server.domain.support.enums.CollationEnum;
import com.alibaba.dbhub.server.domain.support.enums.DbTypeEnum;
import com.alibaba.dbhub.server.domain.support.enums.IndexTypeEnum;
import com.alibaba.dbhub.server.domain.support.model.Sql;
import com.alibaba.dbhub.server.domain.support.model.Table;
import com.alibaba.dbhub.server.domain.support.model.TableColumn;
import com.alibaba.dbhub.server.domain.support.model.TableIndex;
import com.alibaba.dbhub.server.domain.support.model.TableIndexColumn;
import com.alibaba.dbhub.server.domain.support.operations.ConsoleOperations;
import com.alibaba.dbhub.server.domain.support.operations.DataSourceOperations;
import com.alibaba.dbhub.server.domain.support.operations.JdbcOperations;
import com.alibaba.dbhub.server.domain.support.operations.SqlOperations;
import com.alibaba.dbhub.server.domain.support.operations.TableOperations;
import com.alibaba.dbhub.server.domain.support.param.console.ConsoleCreateParam;
import com.alibaba.dbhub.server.domain.support.param.datasource.DataSourceCreateParam;
import com.alibaba.dbhub.server.domain.support.param.sql.SqlAnalyseParam;
import com.alibaba.dbhub.server.domain.support.param.table.DropParam;
import com.alibaba.dbhub.server.domain.support.param.table.ShowCreateTableParam;
import com.alibaba.dbhub.server.domain.support.param.table.TablePageQueryParam;
import com.alibaba.dbhub.server.domain.support.param.table.TableSelector;
import com.alibaba.dbhub.server.domain.support.param.template.TemplateExecuteParam;
import com.alibaba.dbhub.server.test.common.BaseTest;
import com.alibaba.dbhub.server.test.domain.data.service.dialect.DialectProperties;
import com.alibaba.dbhub.server.test.domain.data.utils.TestUtils;
import com.alibaba.dbhub.server.tools.common.util.EasyCollectionUtils;
import com.alibaba.fastjson2.JSON;

import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 数据源测试
 *
 * @author Jiaju Zhuang
 */
@Slf4j
public class TableOperationsTest extends BaseTest {
    /**
     * 表名
     */
    public static final String TABLE_NAME = "data_ops_table_test_" + System.currentTimeMillis();

    @Resource
    private DataSourceOperations dataSourceOperations;
    @Resource
    private ConsoleOperations consoleOperations;
    @Autowired
    private List<DialectProperties> dialectPropertiesList;
    @Resource
    private JdbcOperations jdbcOperations;
    @Resource
    private TableOperations tableOperations;
    @Resource
    private SqlOperations sqlOperations;

    @Test
    @Order(1)
    public void table() {
        for (DialectProperties dialectProperties : dialectPropertiesList) {
            DbTypeEnum dbTypeEnum = dialectProperties.getDbType();
            Long dataSourceId = TestUtils.nextLong();
            Long consoleId = TestUtils.nextLong();

            // 准备上下文
            putConnect(dialectProperties.getUrl(), dialectProperties.getUsername(), dialectProperties.getPassword(),
                dialectProperties.getDbType(), dialectProperties.getDatabaseName(), dataSourceId, consoleId);

            DataSourceCreateParam dataSourceCreateParam = new DataSourceCreateParam();
            dataSourceCreateParam.setDataSourceId(dataSourceId);
            dataSourceCreateParam.setDbType(dbTypeEnum.getCode());
            dataSourceCreateParam.setUrl(dialectProperties.getUrl());
            dataSourceCreateParam.setUsername(dialectProperties.getUsername());
            dataSourceCreateParam.setPassword(dialectProperties.getPassword());
            dataSourceOperations.create(dataSourceCreateParam);

            // 创建控制台
            ConsoleCreateParam consoleCreateParam = new ConsoleCreateParam();
            consoleCreateParam.setDataSourceId(dataSourceId);
            consoleCreateParam.setConsoleId(consoleId);
            consoleCreateParam.setDatabaseName(dialectProperties.getDatabaseName());
            consoleOperations.create(consoleCreateParam);

            // 创建表结构
            List<Sql> sqlList = sqlOperations.analyse(SqlAnalyseParam.builder().dataSourceId(dataSourceId)
                .sql(dialectProperties.getCrateTableSql(TABLE_NAME)).build());
            for (Sql sql : sqlList) {
                TemplateExecuteParam templateQueryParam = new TemplateExecuteParam();
                templateQueryParam.setConsoleId(consoleId);
                templateQueryParam.setDataSourceId(dataSourceId);
                templateQueryParam.setSql(sql.getSql());
                jdbcOperations.execute(templateQueryParam);
            }

            // 查询建表语句
            ShowCreateTableParam showCreateTableParam = ShowCreateTableParam.builder()
                .dataSourceId(dataSourceId)
                .databaseName(dialectProperties.getDatabaseName())
                .tableName(dialectProperties.toCase(TABLE_NAME))
                .build();
            String createTable = tableOperations.showCreateTable(showCreateTableParam);
            log.info("建表语句:{}", createTable);
            if (dialectProperties.getDbType() != DbTypeEnum.H2) {
                Assertions.assertTrue(createTable.contains(dialectProperties.toCase(TABLE_NAME)), "查询表结构失败");
            }

            //  查询表结构
            TablePageQueryParam tablePageQueryParam = new TablePageQueryParam();
            tablePageQueryParam.setDataSourceId(dataSourceId);
            tablePageQueryParam.setDatabaseName(dialectProperties.getDatabaseName());
            tablePageQueryParam.setTableName(dialectProperties.toCase(TABLE_NAME));
            List<Table> tableList = tableOperations.pageQuery(tablePageQueryParam, TableSelector.builder()
                .columnList(Boolean.TRUE)
                .indexList(Boolean.TRUE)
                .build()).getData();
            log.info("分析数据返回{}", JSON.toJSONString(tableList));
            Assertions.assertEquals(1L, tableList.size(), "查询表结构失败");
            Table table = tableList.get(0);
            Assertions.assertEquals(dialectProperties.toCase(TABLE_NAME), table.getName(), "查询表结构失败");
            Assertions.assertEquals("测试表", table.getComment(), "查询表结构失败");

            List<TableColumn> columnList = table.getColumnList();
            Assertions.assertEquals(4L, columnList.size(), "查询表结构失败");
            TableColumn id = columnList.get(0);
            Assertions.assertEquals(dialectProperties.toCase("id"), id.getName(), "查询表结构失败");
            Assertions.assertEquals("主键自增", id.getComment(), "查询表结构失败");
            Assertions.assertTrue(id.getAutoIncrement(), "查询表结构失败");
            Assertions.assertFalse(id.getNullable(), "查询表结构失败");

            TableColumn string = columnList.get(3);
            Assertions.assertEquals(dialectProperties.toCase("string"), string.getName(), "查询表结构失败");
            Assertions.assertTrue(string.getNullable(), "查询表结构失败");
            Assertions.assertEquals("DATA", TestUtils.unWrapperDefaultValue(string.getDefaultValue()),
                "查询表结构失败");

            List<TableIndex> tableIndexList = table.getIndexList();
            Assertions.assertEquals(3L, tableIndexList.size(), "查询表结构失败");
            Map<String, TableIndex> tableIndexMap = EasyCollectionUtils.toIdentityMap(tableIndexList,
                TableIndex::getName);
            TableIndex idxDate = tableIndexMap.get(dialectProperties.toCase(TABLE_NAME + "_idx_date"));
            Assertions.assertEquals("日期索引", idxDate.getComment(), "查询表结构失败");
            Assertions.assertEquals(IndexTypeEnum.NORMAL.getCode(), idxDate.getType(), "查询表结构失败");
            Assertions.assertEquals(1L, idxDate.getColumnList().size(), "查询表结构失败");
            Assertions.assertEquals(dialectProperties.toCase("date"), idxDate.getColumnList().get(0).getName(),
                "查询表结构失败");
            Assertions.assertEquals(CollationEnum.DESC.getCode(), idxDate.getColumnList().get(0).getCollation(),
                "查询表结构失败");

            TableIndex ukNumber = tableIndexMap.get(dialectProperties.toCase(TABLE_NAME + "_uk_number"));
            Assertions.assertEquals("唯一索引", ukNumber.getComment(), "查询表结构失败");
            Assertions.assertEquals(IndexTypeEnum.UNIQUE.getCode(), ukNumber.getType(), "查询表结构失败");

            TableIndex idxNumberString = tableIndexMap.get(dialectProperties.toCase(TABLE_NAME + "_idx_number_string"));
            Assertions.assertEquals(2, idxNumberString.getColumnList().size(), "查询表结构失败");

            // 删除表结构
            DropParam dropParam = DropParam.builder()
                .dataSourceId(dataSourceId)
                .databaseName(dialectProperties.getDatabaseName())
                .tableName(dialectProperties.toCase(TABLE_NAME))
                .build();
            tableOperations.drop(dropParam);
            //  查询表结构
            tablePageQueryParam = new TablePageQueryParam();
            tablePageQueryParam.setDataSourceId(dataSourceId);
            tablePageQueryParam.setDatabaseName(dialectProperties.getDatabaseName());
            tablePageQueryParam.setTableName(dialectProperties.toCase(TABLE_NAME));
            tableList = tableOperations.pageQuery(tablePageQueryParam, TableSelector.builder()
                .columnList(Boolean.TRUE)
                .indexList(Boolean.TRUE)
                .build()).getData();
            log.info("删除表后数据返回{}", JSON.toJSONString(tableList));
            Assertions.assertEquals(0L, tableList.size(), "查询表结构失败");

            // 测试建表语句
            testBuildSql(dialectProperties, dataSourceId, consoleId);

            removeConnect();
        }

    }

    private void testBuildSql(DialectProperties dialectProperties, Long dataSourceId, Long consoleId) {
        if (dialectProperties.getDbType() != DbTypeEnum.MYSQL) {
            log.error("目前测试案例只支持mysql");
            return;
        }
        // 新建表
        //    CREATE TABLE `DATA_OPS_TEMPLATE_TEST_1673093980449`
        //    (
        //    `id`     bigint PRIMARY KEY AUTO_INCREMENT NOT NULL COMMENT '主键自增',
        //    `date`   datetime(3)                          not null COMMENT '日期',
        //    `number` bigint COMMENT '长整型',
        //    `string` VARCHAR(100) default 'DATA' COMMENT '名字',
        //        index DATA_OPS_TEMPLATE_TEST_1673093980449_idx_date (date desc) comment '日期索引',
        //        unique DATA_OPS_TEMPLATE_TEST_1673093980449_uk_number (number) comment '唯一索引',
        //        index DATA_OPS_TEMPLATE_TEST_1673093980449_idx_number_string (number, date) comment '联合索引'
        //) COMMENT ='测试表';
        //        * 大小写看具体的数据库决定：
        //* 创建表表结构 : 测试表
        //       * 字段：
        //* id   主键自增
        //* date 日期 非空
        //       * number 长整型
        //       * string  字符串 长度100 默认值 "DATA"
        //       *
        //* 索引(加上$tableName_ 原因是 有些数据库索引是全局唯一的)：
        //* $tableName_idx_date 日期索引 倒序
        //       * $tableName_uk_number 唯一索引
        //       * $tableName_idx_number_string 联合索引
        String tableName = dialectProperties.toCase("data_ops_table_test_" + System.currentTimeMillis());
        Table newTable = new Table();
        newTable.setName(tableName);
        newTable.setComment("测试表");
        List<TableColumn> tableColumnList = new ArrayList<>();
        newTable.setColumnList(tableColumnList);
        //id
        TableColumn idTableColumn = new TableColumn();
        idTableColumn.setName("id");
        idTableColumn.setAutoIncrement(Boolean.TRUE);
        idTableColumn.setPrimaryKey(Boolean.TRUE);
        idTableColumn.setNullable(Boolean.FALSE);
        idTableColumn.setComment("主键自增");
        idTableColumn.setColumnType("bigint");
        tableColumnList.add(idTableColumn);

        // date
        TableColumn dateTableColumn = new TableColumn();
        dateTableColumn.setName("date");
        dateTableColumn.setNullable(Boolean.FALSE);
        dateTableColumn.setComment("日期");
        dateTableColumn.setColumnType("datetime(3)");
        tableColumnList.add(dateTableColumn);

        // number
        TableColumn numberTableColumn = new TableColumn();
        numberTableColumn.setName("number");
        numberTableColumn.setComment("长整型");
        numberTableColumn.setColumnType("bigint");
        tableColumnList.add(numberTableColumn);

        // string
        TableColumn stringTableColumn = new TableColumn();
        stringTableColumn.setName("string");
        stringTableColumn.setComment("名字");
        stringTableColumn.setColumnType("varchar(100)");
        stringTableColumn.setDefaultValue("DATA");
        tableColumnList.add(stringTableColumn);

        // 索引
        List<TableIndex> tableIndexList = new ArrayList<>();
        newTable.setIndexList(tableIndexList);

        //        index DATA_OPS_TEMPLATE_TEST_1673093980449_idx_date (date desc) comment '日期索引',
        tableIndexList.add(TableIndex.builder()
            .name(tableName + "_idx_date")
            .type(IndexTypeEnum.NORMAL.getCode())
            .comment("日期索引")
            .columnList(Lists.newArrayList(TableIndexColumn.builder()
                .name("date")
                .collation(CollationEnum.DESC.getCode())
                .build()))
            .build());

        //        unique DATA_OPS_TEMPLATE_TEST_1673093980449_uk_number (number) comment '唯一索引',
        tableIndexList.add(TableIndex.builder()
            .name(tableName + "_uk_number")
            .type(IndexTypeEnum.UNIQUE.getCode())
            .comment("唯一索引")
            .columnList(Lists.newArrayList(TableIndexColumn.builder()
                .name("number")
                .build()))
            .build());
        //        index DATA_OPS_TEMPLATE_TEST_1673093980449_idx_number_string (number, date) comment '联合索引'
        tableIndexList.add(TableIndex.builder()
            .name(tableName + "_idx_number_string")
            .type(IndexTypeEnum.NORMAL.getCode())
            .comment("联合索引")
            .columnList(Lists.newArrayList(TableIndexColumn.builder()
                    .name("number")
                    .build(),
                TableIndexColumn.builder()
                    .name("date")
                    .build()))
            .build());
        // 构建sql
        List<Sql> buildTableSqlList = tableOperations.buildSql(null, newTable);
        log.info("创建表的结构语句是:{}", JSON.toJSONString(buildTableSqlList));
        for (Sql sql : buildTableSqlList) {
            TemplateExecuteParam templateQueryParam = new TemplateExecuteParam();
            templateQueryParam.setConsoleId(consoleId);
            templateQueryParam.setDataSourceId(dataSourceId);
            templateQueryParam.setSql(sql.getSql());
            jdbcOperations.execute(templateQueryParam);
        }

        // 校验表结构
        checkTable(tableName, dialectProperties, dataSourceId);

        //  去数据库查询表结构
        TablePageQueryParam tablePageQueryParam = new TablePageQueryParam();
        tablePageQueryParam.setDataSourceId(dataSourceId);
        tablePageQueryParam.setDatabaseName(dialectProperties.getDatabaseName());
        tablePageQueryParam.setTableName(dialectProperties.toCase(tableName));
        List<Table> tableList = tableOperations.pageQuery(tablePageQueryParam, TableSelector.builder()
            .columnList(Boolean.TRUE)
            .indexList(Boolean.TRUE)
            .build()).getData();
        log.info("分析数据返回{}", JSON.toJSONString(tableList));
        Assertions.assertEquals(1L, tableList.size(), "查询表结构失败");
        Table oldTable = tableList.get(0);
        Assertions.assertEquals(dialectProperties.toCase(tableName), oldTable.getName(), "查询表结构失败");
        Assertions.assertEquals("测试表", oldTable.getComment(), "查询表结构失败");

        // 修改表结构
        // 构建sql
        log.info("oldTable：{}", JSON.toJSONString(oldTable));
        log.info("newTable：{}", JSON.toJSONString(newTable));
        buildTableSqlList = tableOperations.buildSql(oldTable, newTable);
        log.info("修改表结构是:{}", JSON.toJSONString(buildTableSqlList));
        Assertions.assertTrue(buildTableSqlList.isEmpty(), "构建sql失败");
        //  重新去查询下 这样有2个对象
        tablePageQueryParam = new TablePageQueryParam();
        tablePageQueryParam.setDataSourceId(dataSourceId);
        tablePageQueryParam.setDatabaseName(dialectProperties.getDatabaseName());
        tablePageQueryParam.setTableName(dialectProperties.toCase(tableName));
        tableList = tableOperations.pageQuery(tablePageQueryParam, TableSelector.builder()
            .columnList(Boolean.TRUE)
            .indexList(Boolean.TRUE)
            .build()).getData();
        newTable = tableList.get(0);

        // 修改字段

        // 新增一个字段
        newTable.getColumnList().add(TableColumn.builder()
            .name("add_string")
            .columnType("varchar(20)")
            .comment("新增的字符串")
            .build());

        // 新增一个索引
        newTable.getIndexList().add(TableIndex.builder()
            .name(tableName + "_idx_string_new")
            .type(IndexTypeEnum.NORMAL.getCode())
            .comment("新的字符串索引")
            .columnList(Lists.newArrayList(TableIndexColumn.builder()
                .name("add_string")
                .collation(CollationEnum.DESC.getCode())
                .build()))
            .build());

        // 查询表结构变更
        log.info("oldTable：{}", JSON.toJSONString(oldTable));
        log.info("newTable：{}", JSON.toJSONString(newTable));
        buildTableSqlList = tableOperations.buildSql(oldTable, newTable);
        log.info("修改表结构是:{}", JSON.toJSONString(buildTableSqlList));

        // 删除表结构
        dropTable(tableName, dialectProperties, dataSourceId);
    }

    private void dropTable(String tableName, DialectProperties dialectProperties, Long dataSourceId) {
        // 删除表结构
        DropParam dropParam = DropParam.builder()
            .dataSourceId(dataSourceId)
            .databaseName(dialectProperties.getDatabaseName())
            .tableName(dialectProperties.toCase(tableName))
            .build();
        tableOperations.drop(dropParam);
        //  查询表结构
        TablePageQueryParam tablePageQueryParam = new TablePageQueryParam();
        tablePageQueryParam.setDataSourceId(dataSourceId);
        tablePageQueryParam.setDatabaseName(dialectProperties.getDatabaseName());
        tablePageQueryParam.setTableName(dialectProperties.toCase(tableName));
        List<Table> tableList = tableOperations.pageQuery(tablePageQueryParam, TableSelector.builder()
            .columnList(Boolean.TRUE)
            .indexList(Boolean.TRUE)
            .build()).getData();
        log.info("删除表后数据返回{}", JSON.toJSONString(tableList));
        Assertions.assertEquals(0L, tableList.size(), "查询表结构失败");
    }

    private void checkTable(String tableName, DialectProperties dialectProperties, Long dataSourceId) {
        //  查询表结构
        TablePageQueryParam tablePageQueryParam = new TablePageQueryParam();
        tablePageQueryParam.setDataSourceId(dataSourceId);
        tablePageQueryParam.setDatabaseName(dialectProperties.getDatabaseName());
        tablePageQueryParam.setTableName(dialectProperties.toCase(tableName));
        List<Table> tableList = tableOperations.pageQuery(tablePageQueryParam, TableSelector.builder()
            .columnList(Boolean.TRUE)
            .indexList(Boolean.TRUE)
            .build()).getData();
        log.info("分析数据返回{}", JSON.toJSONString(tableList));
        Assertions.assertEquals(1L, tableList.size(), "查询表结构失败");
        Table table = tableList.get(0);
        Assertions.assertEquals(dialectProperties.toCase(tableName), table.getName(), "查询表结构失败");
        Assertions.assertEquals("测试表", table.getComment(), "查询表结构失败");

        List<TableColumn> columnList = table.getColumnList();
        Assertions.assertEquals(4L, columnList.size(), "查询表结构失败");
        TableColumn id = columnList.get(0);
        Assertions.assertEquals(dialectProperties.toCase("id"), id.getName(), "查询表结构失败");
        Assertions.assertEquals("主键自增", id.getComment(), "查询表结构失败");
        Assertions.assertTrue(id.getAutoIncrement(), "查询表结构失败");
        Assertions.assertFalse(id.getNullable(), "查询表结构失败");
        Assertions.assertTrue(id.getPrimaryKey(), "查询表结构失败");

        TableColumn string = columnList.get(3);
        Assertions.assertEquals(dialectProperties.toCase("string"), string.getName(), "查询表结构失败");
        Assertions.assertTrue(string.getNullable(), "查询表结构失败");
        Assertions.assertEquals("DATA", TestUtils.unWrapperDefaultValue(string.getDefaultValue()),
            "查询表结构失败");

        List<TableIndex> tableIndexList = table.getIndexList();
        Assertions.assertEquals(3L, tableIndexList.size(), "查询表结构失败");
        Map<String, TableIndex> tableIndexMap = EasyCollectionUtils.toIdentityMap(tableIndexList,
            TableIndex::getName);
        TableIndex idxDate = tableIndexMap.get(dialectProperties.toCase(tableName + "_idx_date"));
        Assertions.assertEquals("日期索引", idxDate.getComment(), "查询表结构失败");
        Assertions.assertEquals(IndexTypeEnum.NORMAL.getCode(), idxDate.getType(), "查询表结构失败");
        Assertions.assertEquals(1L, idxDate.getColumnList().size(), "查询表结构失败");
        Assertions.assertEquals(dialectProperties.toCase("date"), idxDate.getColumnList().get(0).getName(),
            "查询表结构失败");
        Assertions.assertEquals(CollationEnum.DESC.getCode(), idxDate.getColumnList().get(0).getCollation(),
            "查询表结构失败");

        TableIndex ukNumber = tableIndexMap.get(dialectProperties.toCase(tableName + "_uk_number"));
        Assertions.assertEquals("唯一索引", ukNumber.getComment(), "查询表结构失败");
        Assertions.assertEquals(IndexTypeEnum.UNIQUE.getCode(), ukNumber.getType(), "查询表结构失败");

        TableIndex idxNumberString = tableIndexMap.get(dialectProperties.toCase(tableName + "_idx_number_string"));
        Assertions.assertEquals(2, idxNumberString.getColumnList().size(), "查询表结构失败");
    }

    @Test
    @Order(Integer.MAX_VALUE)
    public void dropTable() {
        for (DialectProperties dialectProperties : dialectPropertiesList) {
            try {
                DbTypeEnum dbTypeEnum = dialectProperties.getDbType();
                Long dataSourceId = TestUtils.nextLong();
                Long consoleId = TestUtils.nextLong();

                DataSourceCreateParam dataSourceCreateParam = new DataSourceCreateParam();
                dataSourceCreateParam.setDataSourceId(dataSourceId);
                dataSourceCreateParam.setDbType(dbTypeEnum.getCode());
                dataSourceCreateParam.setUrl(dialectProperties.getUrl());
                dataSourceCreateParam.setUsername(dialectProperties.getUsername());
                dataSourceCreateParam.setPassword(dialectProperties.getPassword());
                dataSourceOperations.create(dataSourceCreateParam);

                // 创建控制台
                ConsoleCreateParam consoleCreateParam = new ConsoleCreateParam();
                consoleCreateParam.setDataSourceId(dataSourceId);
                consoleCreateParam.setConsoleId(consoleId);
                consoleCreateParam.setDatabaseName(dialectProperties.getDatabaseName());
                consoleOperations.create(consoleCreateParam);

                // 创建表结构
                TemplateExecuteParam templateQueryParam = new TemplateExecuteParam();
                templateQueryParam.setConsoleId(consoleId);
                templateQueryParam.setDataSourceId(dataSourceId);
                templateQueryParam.setSql(dialectProperties.getDropTableSql(TABLE_NAME));
                jdbcOperations.execute(templateQueryParam);
            } catch (Exception e) {
                log.warn("删除表结构失败.", e);
            }
        }
    }

}
