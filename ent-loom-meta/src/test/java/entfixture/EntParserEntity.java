//package entfixture;
//
//import com.pro.framework.jts.annotation.EnumJtsSqlType;
//import com.pro.framework.jts.annotation.JtsColumn;
//import com.pro.framework.jts.annotation.JtsIntEnum;
//import com.pro.framework.jts.annotation.JtsStringEnum;
//import com.pro.framework.jts.annotation.JtsTable;
//import com.entloom.meta.annotations.EntField;
//import com.entloom.meta.annotations.EntEntity;
//import com.entloom.meta.enums.EntFieldKind;
//import com.entloom.meta.annotations.meta.EntMetaDateTime;
//import com.entloom.meta.annotations.meta.EntMetaEnum;
//import com.entloom.meta.annotations.meta.EntMetaFlag;
//import com.entloom.meta.annotations.meta.EntMetaId;
//import com.entloom.meta.annotations.meta.EntMetaJson;
//import com.entloom.meta.annotations.meta.EntMetaMedia;
//import com.entloom.meta.annotations.meta.EntMetaNumber;
//import com.entloom.meta.annotations.meta.EntMetaRefId;
//import com.entloom.meta.annotations.meta.EntMetaText;
//import com.entloom.meta.enums.role.DateTimeRole;
//import com.entloom.meta.enums.role.EnumRole;
//import com.entloom.meta.enums.role.FlagRole;
//import com.entloom.meta.enums.role.JsonRole;
//import com.entloom.meta.enums.role.MediaRole;
//import com.entloom.meta.enums.role.NumberRole;
//import com.entloom.meta.enums.role.RefIdRole;
//import com.entloom.meta.enums.role.TextRole;
//
//import java.math.BigDecimal;
//import java.time.LocalDateTime;
//
//@JtsTable(name = "ent_parser_entity", primaryKey = {"id"})
//@EntEntity(
//        entity = "user",
//        service = "userService",
//        label = "用户",
//        description = "平台用户",
//        defaultLabelFields = {"name", "mobile"},
//        plannedVolume = 20000
//)
//public class EntParserEntity {
//
//    @EntField(EntFieldKind.ID)
//    @EntMetaId
//    private Long id;
//
//    @EntField(EntFieldKind.REF_ID)
//    @EntMetaRefId(value = RefIdRole.GENERIC, refService = "userService", refEntity = "user")
//    private Long ownerId;
//
//    @EntField(EntFieldKind.ENUM)
//    @EntMetaEnum(value = EnumRole.STATUS, valueType = EntMetaEnum.ValueType.INT)
//    private BizStatus taskStatus;
//
//    @EntField(EntFieldKind.ENUM)
//    private BizStatus interfaceStatus;
//
//    @EntField(EntFieldKind.ENUM)
//    private BizType plainType;
//
//    @EntField(EntFieldKind.ENUM)
//    private BizCode codeType;
//
//    @EntField(EntFieldKind.FLAG)
//    @EntMetaFlag(FlagRole.SOFT_DELETE)
//    private Boolean deleted;
//
//    @EntField(EntFieldKind.DATETIME)
//    @EntMetaDateTime(value = DateTimeRole.CREATED_TIME, encoding = EntMetaDateTime.TimeEncoding.EPOCH_MILLIS)
//    private Long createdAtEpoch;
//
//    private Long eventTime;
//
//    @EntMetaDateTime(DateTimeRole.UPDATED_TIME)
//    private LocalDateTime updateTime;
//
//    @EntField(EntFieldKind.TEXT)
//    @EntMetaText(TextRole.GENERIC)
//    @JtsColumn(sqlType = EnumJtsSqlType.VARCHAR, length = 64)
//    private String title;
//
//    @EntField(EntFieldKind.MEDIA)
//    @EntMetaMedia(MediaRole.IMAGE)
//    private String materialImageUrl;
//
//    @EntField(EntFieldKind.MEDIA)
//    @EntMetaMedia(MediaRole.IMAGE)
//    private String carouselImageUrls;
//
//    @EntField(EntFieldKind.RICH_CONTENT)
//    private String goodsDesc;
//
//    @EntField(EntFieldKind.JSON_DOC)
//    @EntMetaJson(JsonRole.GENERIC)
//    private String catSuggestionResponseJson;
//
//    @EntField(EntFieldKind.NUMBER)
//    @EntMetaNumber(NumberRole.MONEY)
//    private BigDecimal priceAmount;
//
//    public enum BizStatus implements JtsIntEnum {
//        OFF(0),
//        ON(1);
//
//        private final int value;
//
//        BizStatus(int value) {
//            this.value = value;
//        }
//
//        @Override
//        public int getValue() {
//            return value;
//        }
//    }
//
//    public enum BizType {
//        BASIC,
//        ADVANCED
//    }
//
//    public enum BizCode implements JtsStringEnum {
//        ALPHA("A"),
//        BETA("B");
//
//        private final String code;
//
//        BizCode(String code) {
//            this.code = code;
//        }
//
//        @Override
//        public String getCode() {
//            return code;
//        }
//    }
//}
