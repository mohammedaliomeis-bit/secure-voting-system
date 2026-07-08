package com.securevoting.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.BeanWrapperImpl;

public class PasswordsMatchValidator implements ConstraintValidator<PasswordsMatch, Object> {

    private String passwordField;
    private String confirmField;

    @Override
    public void initialize(PasswordsMatch ann) {
        this.passwordField = ann.passwordField();
        this.confirmField = ann.confirmField();
    }

    @Override
    public boolean isValid(Object target, ConstraintValidatorContext ctx) {
        if (target == null) return true;
        BeanWrapperImpl wrapper = new BeanWrapperImpl(target);
        Object pw = wrapper.getPropertyValue(passwordField);
        Object confirm = wrapper.getPropertyValue(confirmField);
        boolean ok = pw != null && pw.equals(confirm);
        if (!ok) {
            ctx.disableDefaultConstraintViolation();
            ctx.buildConstraintViolationWithTemplate(ctx.getDefaultConstraintMessageTemplate())
                    .addPropertyNode(confirmField)
                    .addConstraintViolation();
        }
        return ok;
    }
}