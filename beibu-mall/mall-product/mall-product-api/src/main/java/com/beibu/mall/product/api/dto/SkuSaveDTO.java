package com.beibu.mall.product.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SkuSaveDTO {

    private Long id;

    @NotNull(message = "SPU ID 不能为空")
    private Long spuId;

    @NotBlank(message = "规格描述不能为空")
    private String spec;

    @NotNull(message = "价格不能为空")
    private BigDecimal price;

    private String unit;

    private Integer stock;

    private String img;
}
