package com.project.playground.global.p6spy;

import com.p6spy.engine.spy.appender.MessageFormattingStrategy;
import org.hibernate.engine.jdbc.internal.FormatStyle;

public class CustomP6SpyLogFormat implements MessageFormattingStrategy {

    @Override
    public String formatMessage(int connectionId, String now, long elapsed, String category, String prepared, String sql, String url) {
        String query = resolveQuery(sql, prepared);
        if (query.isBlank()) {
            return "";
        }
        return category + " " + elapsed + "ms" + format(query);
    }

    private String resolveQuery(String sql, String prepared) {
        if (sql != null && !sql.isBlank()) {
            return sql;
        }
        if (prepared != null && !prepared.isBlank()) {
            return prepared;
        }
        return "";
    }

    private String format(String query) {
        try {
            return FormatStyle.BASIC.getFormatter().format(query);
        } catch (RuntimeException exception) {
            return System.lineSeparator() + query;
        }
    }
}
