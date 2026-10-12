package com.project.playground.global.validation;

import jakarta.validation.ConstraintDeclarationException;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class StringValidator implements ConstraintValidator<StringValid, CharSequence> {

    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "^(?:010-[0-9]{4}-[0-9]{4}|01[16789]-[0-9]{3,4}-[0-9]{4}|02-[0-9]{3,4}-[0-9]{4}|0(?:3[1-3]|4[1-4]|5[1-5]|6[1-4])-[0-9]{3,4}-[0-9]{4}|070-[0-9]{4}-[0-9]{4})$"
    );

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^(?=.{1,254}$)(?=[^@]{1,64}@)"
                    + "[A-Za-z0-9_%+-]+(?:\\.[A-Za-z0-9_%+-]+)*@"
                    + "(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)+"
                    + "[A-Za-z]{2,63}$"
    );

    private static final Pattern NUMBER_HYPHEN_PATTERN = Pattern.compile(
            "^\\d+(?:-\\d+)*$"
    );

    private String label;

    private StringValidType type;

    private int min;

    private int max;

    private boolean weightedLength;

    private boolean required;

    private boolean allowSpace;

    private boolean allowNumber;

    private String customMessage;

    private String extraAllowedChars;

    private Set<Integer> extraAllowedCodePoints;

    @Override
    public void initialize(StringValid valid) {
        validateConfig(valid);

        this.label = valid.label().strip();
        this.type = valid.type();
        this.min = valid.min();
        this.max = valid.max();
        this.weightedLength = valid.weightedLength();
        this.required = valid.required();
        this.allowSpace = valid.allowSpace();
        this.allowNumber = valid.allowNumber();
        this.customMessage = valid.message();
        this.extraAllowedChars = valid.extraAllowedChars();
        this.extraAllowedCodePoints = toAllowedCodePointSet(this.extraAllowedChars);
    }

    @Override
    public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) {
            return !required || fail(context, requiredMessage());
        }

        String text = value.toString();

        if (isBlankOnly(text)) {
            return fail(context, blankOnlyMessage());
        }

        if (!matchesType(text)) {
            return fail(context, typeMessage());
        }

        if (!validateLength(text)) {
            return fail(context, lengthMessage());
        }

        return true;
    }

    private boolean isBlankOnly(String text) {
        return text.codePoints().allMatch(codePoint ->
                Character.isWhitespace(codePoint)
                        || Character.isSpaceChar(codePoint)
        );
    }

    private String blankOnlyMessage() {
        return fieldPrefix() + "공백만 입력할 수 없습니다.";
    }

    private boolean matchesType(String text) {
        return switch (type) {
            case ANY -> true;
            case KOR, ENG, KOR_ENG -> matchesByCharacterPolicy(text);
            case PHONE -> PHONE_PATTERN.matcher(text).matches();
            case EMAIL -> EMAIL_PATTERN.matcher(text).matches();
            case NUMBER_HYPHEN -> NUMBER_HYPHEN_PATTERN.matcher(text).matches();
        };
    }

    private boolean matchesByCharacterPolicy(String text) {
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);

            boolean allowed =
                    (allowSpace && codePoint == ' ')
                            || (allowNumber && isAsciiNumber(codePoint))
                            || extraAllowedCodePoints.contains(codePoint)
                            || isAllowedBaseCharacter(codePoint);

            if (!allowed) {
                return false;
            }

            i += Character.charCount(codePoint);
        }

        return true;
    }

    private boolean isAllowedBaseCharacter(int codePoint) {
        return switch (type) {
            case KOR -> isKorean(codePoint);
            case ENG -> isEnglish(codePoint);
            case KOR_ENG -> isKorean(codePoint) || isEnglish(codePoint);
            default -> throw new IllegalArgumentException();
        };
    }

    private boolean validateLength(String text) {
        if (isLengthUnlimited()) {
            return true;
        }

        int length = lengthOf(text);

        return length >= min && length <= max;
    }

    private int lengthOf(String text) {
        if (weightedLength) {
            return weightedLength(text);
        }

        return codePointLength(text);
    }

    private boolean isLengthUnlimited() {
        return min == 0 && max == StringValid.UNLIMITED_MAX;
    }

    private void validateConfig(StringValid valid) {
        if (valid.label().isBlank()) {
            throw invalidConfig("@StringValid label은 필수이며 비어 있을 수 없습니다.");
        }

        if (valid.min() < 0) {
            throw invalidConfig("@StringValid min은 0 이상이어야 합니다.");
        }

        if (valid.max() < 1) {
            throw invalidConfig("@StringValid max는 1 이상이어야 합니다.");
        }

        if (valid.min() > valid.max()) {
            throw invalidConfig("@StringValid min은 max보다 작거나 같아야 합니다.");
        }

        if (!valid.type().supportsCharacterPolicyOptions() && valid.allowSpace()) {
            throw invalidConfig("@StringValid allowSpace는 KOR, ENG, KOR_ENG 타입에서만 사용할 수 있습니다.");
        }

        if (!valid.type().supportsCharacterPolicyOptions() && valid.allowNumber()) {
            throw invalidConfig("@StringValid allowNumber는 KOR, ENG, KOR_ENG 타입에서만 사용할 수 있습니다.");
        }

        if (!valid.type().supportsCharacterPolicyOptions() && !valid.extraAllowedChars().isEmpty()) {
            throw invalidConfig("@StringValid extraAllowedChars는 KOR, ENG, KOR_ENG 타입에서만 사용할 수 있습니다.");
        }

        if (valid.extraAllowedChars().codePoints().anyMatch(this::isDisallowedSpaceCharacter)) {
            throw invalidConfig("@StringValid extraAllowedChars에는 공백 또는 제어문자를 포함할 수 없습니다. 공백 허용은 allowSpace=true를 사용하세요.");
        }

        if (!valid.type().supportsWeightedLength() && valid.weightedLength()) {
            throw invalidConfig("@StringValid weightedLength는 ANY, KOR, ENG, KOR_ENG 타입에서만 사용할 수 있습니다.");
        }
    }

    private ConstraintDeclarationException invalidConfig(String message) {
        return new ConstraintDeclarationException(message);
    }

    private boolean isDisallowedSpaceCharacter(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint) || Character.isISOControl(codePoint);
    }

    private Set<Integer> toAllowedCodePointSet(String value) {
        return value.codePoints().boxed().collect(Collectors.toUnmodifiableSet());
    }

    private int codePointLength(String text) {
        return text.codePointCount(0, text.length());
    }

    private int weightedLength(String text) {
        int length = 0;

        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);

            length += isAscii(codePoint) ? 1 : 2;

            i += Character.charCount(codePoint);
        }

        return length;
    }

    private boolean isAscii(int codePoint) {
        return codePoint >= 0x00 && codePoint <= 0x7F;
    }

    private boolean isKorean(int codePoint) {
        return codePoint >= '가' && codePoint <= '힣';
    }

    private boolean isEnglish(int codePoint) {
        return (codePoint >= 'A' && codePoint <= 'Z') || (codePoint >= 'a' && codePoint <= 'z');
    }

    private boolean isAsciiNumber(int codePoint) {
        return codePoint >= '0' && codePoint <= '9';
    }

    private String requiredMessage() {
        return fieldPrefix() + "항목을 입력해주세요.";
    }

    private String typeMessage() {
        if (!type.supportsCharacterPolicyOptions()) {
            return fieldPrefix() + type.message();
        }

        if (!hasCharacterPolicyOptions()) {
            return fieldPrefix() + type.message();
        }

        return fieldPrefix() + allowedCharacterPolicyText() + "만 입력 가능합니다.";
    }

    private boolean hasCharacterPolicyOptions() {
        return allowNumber || allowSpace || !extraAllowedChars.isEmpty();
    }

    private String allowedCharacterPolicyText() {
        StringBuilder message = new StringBuilder();

        appendBaseAllowedText(message);

        if (allowNumber) {
            appendAllowedText(message, "숫자");
        }

        if (allowSpace) {
            appendAllowedText(message, "공백");
        }

        if (!extraAllowedChars.isEmpty()) {
            appendAllowedText(message, "추가 문자 " + extraAllowedCharsMessage());
        }

        return message.toString();
    }

    private String extraAllowedCharsMessage() {
        Set<Integer> codePoints = new LinkedHashSet<>();
        extraAllowedChars.codePoints().forEach(codePoints::add);

        StringBuilder message = new StringBuilder();

        for (int codePoint : codePoints) {
            if (!message.isEmpty()) {
                message.append(", ");
            }

            message.append(escapeMessageTemplate(new String(Character.toChars(codePoint))));
        }

        return message.toString();
    }

    private void appendBaseAllowedText(StringBuilder message) {
        switch (type) {
            case KOR -> appendAllowedText(message, "한글");
            case ENG -> appendAllowedText(message, "영문");
            case KOR_ENG -> {
                appendAllowedText(message, "한글");
                appendAllowedText(message, "영문");
            }
            case ANY, PHONE, EMAIL, NUMBER_HYPHEN -> throw new IllegalArgumentException();
        }
    }

    private void appendAllowedText(StringBuilder message, String text) {
        if (!message.isEmpty()) {
            message.append(", ");
        }

        message.append(text);
    }

    private String escapeMessageTemplate(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("{", "\\{")
                .replace("}", "\\}")
                .replace("$", "\\$");
    }

    private String normalLengthMessage() {
        if (max == StringValid.UNLIMITED_MAX) {
            return fieldPrefix() + "허용 길이는 " + min + "자 이상입니다.";
        }

        if (min == 0) {
            return fieldPrefix() + "허용 길이는 " + max + "자 이하입니다.";
        }

        return fieldPrefix() + "허용 길이는 " + min + "~" + max + "자입니다.";
    }

    private String lengthMessage() {
        if (weightedLength) {
            return weightedLengthMessage();
        }

        return normalLengthMessage();
    }

    private String weightedLengthMessage() {
        String guide = lengthCalculationGuide();

        if (max == StringValid.UNLIMITED_MAX) {
            return fieldPrefix() + "허용 길이는 " + min + "자 이상입니다.\n" + guide;
        }

        if (min == 0) {
            return fieldPrefix() + "허용 길이는 " + max + "자 이하입니다.\n" + guide;
        }

        return fieldPrefix() + "허용 길이는 " + min + "~" + max + "자입니다.\n" + guide;
    }

    private String lengthCalculationGuide() {
        StringBuilder doubleWidth = new StringBuilder();
        StringBuilder singleWidth = new StringBuilder();

        switch (type) {
            case KOR -> appendGuideText(doubleWidth, "한글");
            case ENG -> appendGuideText(singleWidth, "영문");
            case KOR_ENG -> {
                appendGuideText(doubleWidth, "한글");
                appendGuideText(singleWidth, "영문");
            }
            case ANY, PHONE, EMAIL, NUMBER_HYPHEN -> {
                return "영문, 숫자, 기호는 1자, 그 외 문자는 2자로 계산합니다.";
            }
        }

        if (allowNumber) {
            appendGuideText(singleWidth, "숫자");
        }

        if (allowSpace) {
            appendGuideText(singleWidth, "공백");
        }

        if (doubleWidth.isEmpty()) {
            return singleWidth + subjectParticle(singleWidth) + " 1자로 계산합니다.";
        }

        if (singleWidth.isEmpty()) {
            return doubleWidth + subjectParticle(doubleWidth) + " 2자로 계산합니다.";
        }

        return doubleWidth + subjectParticle(doubleWidth) + " 2자, "
                + singleWidth + subjectParticle(singleWidth) + " 1자로 계산합니다.";
    }

    private void appendGuideText(StringBuilder message, String text) {
        if (!message.isEmpty()) {
            message.append(", ");
        }

        message.append(text);
    }

    private String subjectParticle(CharSequence text) {
        int lastCodePoint = Character.codePointBefore(text, text.length());

        if (lastCodePoint < '가' || lastCodePoint > '힣') {
            return "은";
        }

        int jongSungIndex = (lastCodePoint - '가') % 28;

        return jongSungIndex == 0 ? "는" : "은";
    }

    private String fieldPrefix() {
        return "[" + escapeMessageTemplate(label) + "] ";
    }

    private boolean fail(ConstraintValidatorContext context, String defaultMessage) {
        String message = hasCustomMessage()
                ? escapeMessageTemplate(customMessage)
                : defaultMessage;

        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
                .addConstraintViolation();

        return false;
    }

    private boolean hasCustomMessage() {
        return customMessage != null && !customMessage.isBlank();
    }
}
