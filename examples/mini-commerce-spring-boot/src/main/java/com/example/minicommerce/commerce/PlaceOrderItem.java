package com.example.minicommerce.commerce;

/** 下单请求中的商品行。 */
public class PlaceOrderItem {
    /** 商品主键。 */
    private Long productId;

    /** 购买数量。 */
    private Integer quantity;

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}
