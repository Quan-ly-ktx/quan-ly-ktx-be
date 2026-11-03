package com.uit.dormmanagement.dtos.response.base;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@AllArgsConstructor
@SuperBuilder
@JsonPropertyOrder({"httpStatus", "message"})
public class SuccessResponse extends AbstractBaseResponse {}
