package com.beibu.mall.seckill.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class SeckillResultStatus implements Serializable {
    private static final long serialVersionUID = 1L;
    private String status;
    private String message;
    private Long orderId;
}
