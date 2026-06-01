package com.beibu.mall.user.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 收货地址请求参数（新增和修改共用）
 */
@Data
public class AddressDTO {

    /** 地址ID（修改时必填，新增时不用传） */
    private Long id;

    @NotBlank(message = "收货人姓名不能为空")
    private String receiverName;

    @NotBlank(message = "收货人手机号不能为空")
    private String receiverPhone;

    @NotBlank(message = "省不能为空")
    private String province;

    @NotBlank(message = "市不能为空")
    private String city;

    @NotBlank(message = "区不能为空")
    private String district;

    @NotBlank(message = "详细地址不能为空")
    private String detail;

    /** 是否设为默认地址 */
    private Integer isDefault;
}
