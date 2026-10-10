package com.zlth.ivd.system.vo;
public record InstitutionVO(Long id,String code,String name,String type,Long ownerInstitutionId,String contactPerson,
        String contactPhone,String address,String capabilities,Integer status) { }
