package com.entloom.ddl.mysql.fixture;

import com.entloom.ddl.annotations.EntDbEntity;
import com.entloom.ddl.annotations.EntDbField;
import com.entloom.ddl.annotations.EntDbIndex;
import com.entloom.ddl.enums.NamingStrategy;
import com.entloom.ddl.enums.GenerationStrategy;

/** MySQL 8 集成测试使用的最小业务实体。 */
@EntDbEntity(table = "mysql_account", namingStrategy = NamingStrategy.AS_IS)
@EntDbIndex(name = "idx_mysql_account_display_name", fields = {"display_name"})
@EntDbIndex(name = "idx_mysql_account_lower_name", expression = "lower(`display_name`)")
public final class MysqlAccount {
    /** 主键。 */
    @EntDbField(comment = "主键", generationStrategy = GenerationStrategy.AUTO_INCREMENT)
    private Long id;

    /** 展示名称。 */
    @EntDbField(column = "display_name", length = 80, comment = "展示名称")
    private String displayName;
}
