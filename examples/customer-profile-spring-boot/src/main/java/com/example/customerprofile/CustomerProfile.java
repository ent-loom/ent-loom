package com.example.customerprofile;

import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.base.common.OptionalBoolean;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.annotations.EntField;

/**
 * The only entity in this example.
 */
@EntEntity(
    entity = "customer_profile",
    value = "Customer Profile",
    service = "customer-profile"
)
@EntCrudEntity(
    name = "customer_profile",
    table = "customer_profile",
    ownerService = "customer-profile"
)
public class CustomerProfile {
    @EntField("ID")
    private Long id;

    @EntField(value = "Display name", required = OptionalBoolean.TRUE)
    private String displayName;

    @EntField(value = "Email", required = OptionalBoolean.TRUE)
    private String email;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
