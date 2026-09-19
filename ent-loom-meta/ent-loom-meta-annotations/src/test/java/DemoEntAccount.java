import com.entloom.meta.annotations.EntField;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.annotations.EntIndex;
import com.entloom.meta.annotations.meta.EntMetaDateTime;
import com.entloom.meta.annotations.meta.EntMetaEnum;
import com.entloom.meta.annotations.meta.EntMetaFlag;
import com.entloom.meta.annotations.meta.EntMetaId;
import com.entloom.meta.annotations.meta.EntMetaText;
import com.entloom.meta.enums.role.DateTimeRole;
import com.entloom.meta.enums.role.EnumRole;
import com.entloom.meta.enums.role.FlagRole;
import com.entloom.meta.enums.role.TextRole;

import java.time.LocalDateTime;

/**
 * EntEntity 账号示例。
 */
@EntEntity(
        entity = "account",
        service = "account-center",
        label = "账号",
        description = "平台账号基础信息",
        plannedVolume = 300000
)
@EntIndex(name = "uk_account_mobile", fields = {"mobile"}, unique = true)
public class DemoEntAccount {

    @EntField
    @EntMetaId
    private Long id;

    @EntField
    @EntMetaText(TextRole.GENERIC)
    private String nickname;

    @EntField
    @EntMetaText(TextRole.GENERIC)
    private String mobile;

    @EntField
    @EntMetaEnum(value = EnumRole.STATUS, valueType = EntMetaEnum.ValueType.STRING)
    private AccountStatus status;

    @EntField
    @EntMetaFlag(FlagRole.ENABLED)
    private Boolean enabled;

    @EntField
    @EntMetaDateTime(DateTimeRole.CREATED_TIME)
    private LocalDateTime createdAt;

    public enum AccountStatus {
        PENDING,
        ACTIVE,
        DISABLED
    }
}
