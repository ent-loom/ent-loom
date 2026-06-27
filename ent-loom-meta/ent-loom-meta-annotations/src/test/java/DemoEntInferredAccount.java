import com.entloom.meta.annotations.EntEntity;

import java.time.LocalDateTime;

/**
 * 纯推断示例：不显式写 @EntField。
 */
@EntEntity(
        entity = "inferred_account",
        service = "account-center",
        label = "推断账号",
        description = "根据字段名和字段类型自动推断语义",
        defaultLabelFields = {"nickname", "mobile"},
        plannedVolume = 300000
)
public class DemoEntInferredAccount {

    private Long id;

    private Long tenantId;

    private String nickname;

    private String mobile;

    private String inviteCode;

    private AccountStatus status;

    private Boolean enabled;

    private Boolean deleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private String profileJson;

    public enum AccountStatus {
        INIT,
        ACTIVE,
        FROZEN
    }
}
