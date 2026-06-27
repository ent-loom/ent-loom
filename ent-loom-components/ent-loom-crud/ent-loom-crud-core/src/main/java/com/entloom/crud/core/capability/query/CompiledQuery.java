package com.entloom.crud.core.capability.query;

import com.entloom.crud.core.runtime.meta.RelationEdge;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 编译后的查询对象。
 */
@Getter
@Setter
public class CompiledQuery {
    /** 数据查询 SQL。 */
    private String dataSql;
    /** 数据查询参数列表。 */
    private List<Object> dataArgs = new ArrayList<>();
    /** 统计 SQL。 */
    private String countSql;
    /** 统计参数列表。 */
    private List<Object> countArgs = new ArrayList<>();
    /** 展开关联边列表。 */
    private List<RelationEdge> expandEdges = new ArrayList<>();
    /** 查询执行计划。 */
    private QueryPlan queryPlan;

}
