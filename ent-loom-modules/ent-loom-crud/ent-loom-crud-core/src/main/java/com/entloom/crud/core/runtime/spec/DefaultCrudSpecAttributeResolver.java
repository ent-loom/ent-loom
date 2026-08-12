package com.entloom.crud.core.runtime.spec;

import com.entloom.crud.api.enums.CrudErrorCode;
import com.entloom.crud.core.exception.CrudException;
import com.entloom.crud.core.governance.scope.CrudDataScope;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 默认 Spec 属性合并器。
 */
public class DefaultCrudSpecAttributeResolver implements CrudSpecAttributeResolver {
    /** 最大属性 key 长度。 */
    private static final int MAX_KEY_LENGTH = 128;

    /** 属性贡献器列表。 */
    private final List<CrudSpecAttributeContributor> contributors;
    /** 业务保留属性 key。 */
    private final Set<String> additionalReservedKeys;

    public DefaultCrudSpecAttributeResolver() {
        this(null);
    }

    public DefaultCrudSpecAttributeResolver(Collection<CrudSpecAttributeContributor> contributors) {
        this(contributors, null);
    }

    public DefaultCrudSpecAttributeResolver(
        Collection<CrudSpecAttributeContributor> contributors,
        Collection<String> additionalReservedKeys
    ) {
        this.contributors = new ArrayList<CrudSpecAttributeContributor>();
        this.additionalReservedKeys = new LinkedHashSet<String>();
        if (contributors != null) {
            for (CrudSpecAttributeContributor contributor : contributors) {
                if (contributor != null) {
                    this.contributors.add(contributor);
                }
            }
        }
        if (additionalReservedKeys != null) {
            for (String key : additionalReservedKeys) {
                if (key != null && !key.trim().isEmpty()) {
                    this.additionalReservedKeys.add(key);
                }
            }
        }
    }

    @Override
    public Map<String, Object> resolve(BaseSpec spec) {
        LinkedHashMap<String, Object> attributes = new LinkedHashMap<String, Object>();
        if (spec != null) {
            attributes.putAll(spec.getAttributes());
        }
        stripReservedGovernanceKeys(attributes);
        for (CrudSpecAttributeContributor contributor : contributors) {
            Map<String, Object> contributed = contributor.contribute(spec);
            mergeContributed(attributes, contributed);
        }
        return attributes;
    }

    private void stripReservedGovernanceKeys(Map<String, Object> attributes) {
        for (String key : CrudSpecAttributeKeys.RESERVED_GOVERNANCE_KEYS) {
            attributes.remove(key);
        }
        for (String key : additionalReservedKeys) {
            attributes.remove(key);
        }
    }

    private void mergeContributed(Map<String, Object> attributes, Map<String, Object> contributed) {
        if (contributed == null || contributed.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : contributed.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }
            validateKey(key);
            validateReservedValue(key, value);
            attributes.put(key, value);
        }
    }

    private void validateKey(String key) {
        if (key == null || key.trim().isEmpty()) {
            throw attributeException("attribute key 不能为空");
        }
        if (key.length() > MAX_KEY_LENGTH) {
            throw attributeException("attribute key 长度不能超过 " + MAX_KEY_LENGTH + ": " + key);
        }
        for (int i = 0; i < key.length(); i++) {
            char ch = key.charAt(i);
            if (ch < 0x20 || ch == 0x7F) {
                throw attributeException("attribute key 不能包含控制字符: " + key);
            }
        }
    }

    private void validateReservedValue(String key, Object value) {
        if (CrudSpecAttributeKeys.CRUD_DATA_SCOPE.equals(key)) {
            if (!(value instanceof CrudDataScope)) {
                throw attributeException("crudDataScope 必须是 CrudDataScope");
            }
            return;
        }
        if (CrudSpecAttributeKeys.CRUD_EXPLICIT_ALL.equals(key)) {
            if (!Boolean.TRUE.equals(value)) {
                throw attributeException("crudExplicitAll 只接受 Boolean.TRUE");
            }
            return;
        }
        if (CrudSpecAttributeKeys.CRUD_DATA_SCOPE_DIMENSIONS.equals(key)) {
            if (!(value instanceof Map<?, ?>) || ((Map<?, ?>) value).isEmpty()) {
                throw attributeException("crudDataScopeDimensions 必须是非空 Map");
            }
        }
    }

    private CrudException attributeException(String message) {
        return new CrudException(CrudErrorCode.ATTRIBUTE_CONTRIBUTION_FAILED, message);
    }
}
