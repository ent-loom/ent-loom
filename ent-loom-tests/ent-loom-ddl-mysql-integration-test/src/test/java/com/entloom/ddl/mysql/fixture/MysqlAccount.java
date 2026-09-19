package com.entloom.ddl.mysql.fixture;

import com.entloom.ddl.annotations.EntDdlEntity;
import com.entloom.ddl.annotations.EntDdlField;
import com.entloom.ddl.annotations.EntDdlIndex;
import com.entloom.ddl.enums.NamingStrategy;
import com.entloom.ddl.enums.GenerationStrategy;

/** MySQL 8 集成测试使用的最小业务实体。 */
@EntDdlEntity(table = "mysql_account", namingStrategy = NamingStrategy.AS_IS)
@EntDdlIndex(name = "idx_mysql_account_display_name", fields = {"display_name"})
@EntDdlIndex(name = "idx_mysql_account_lower_name", expression = "lower(`display_name`)")
public final class MysqlAccount {
    /** 主键。 */
    @EntDdlField(comment = "主键", generationStrategy = GenerationStrategy.AUTO_INCREMENT)
    private Long id;

    /** 展示名称。 */
    @EntDdlField(column = "display_name", length = 80, comment = "展示名称")
    private String displayName;
}
