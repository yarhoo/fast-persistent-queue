package com.btoddb.fastpersitentqueue.eventbus.routers;

import com.btoddb.fastpersitentqueue.eventbus.Config;
import com.btoddb.fastpersitentqueue.eventbus.EventBusComponentBaseImpl;
import com.btoddb.fastpersitentqueue.eventbus.FpqEvent;
import com.btoddb.fastpersitentqueue.eventbus.FpqRouter;
import com.btoddb.fastpersitentqueue.eventbus.PlunkerRunner;
import com.btoddb.fastpersitentqueue.eventbus.routers.expressions.AndExpression;
import com.btoddb.fastpersitentqueue.eventbus.routers.expressions.Expression;
import com.btoddb.fastpersitentqueue.eventbus.routers.expressions.HeaderExpression;
import com.btoddb.fastpersitentqueue.eventbus.routers.expressions.OrExpression;
import com.btoddb.fastpersitentqueue.eventbus.routers.expressions.StringBodyExpression;
import com.btoddb.fastpersitentqueue.exceptions.FpqException;
import com.google.common.base.Splitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Routes all events received by the named {@link com.btoddb.fastpersitentqueue.eventbus.FpqCatcher} to the
 * specified {@link com.btoddb.fastpersitentqueue.eventbus.FpqPlunker}.
 */
public class ConditionalRouterImpl extends EventBusComponentBaseImpl implements FpqRouter {
    private static final Logger logger = LoggerFactory.getLogger(ConditionalRouterImpl.class);

    private static final Pattern REGEX_AND = Pattern.compile("(.+) +(AND) +(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern REGEX_OR = Pattern.compile("(.+) +(OR) +(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern REGEX_EXPRESSION = Pattern.compile("([^\\s]+)\\s*([=!]+)\\s*([^\\s]+)");
    private static final Pattern REGEX_HEADER_VAR = Pattern.compile("headers\\[(.+)\\]", Pattern.CASE_INSENSITIVE);

    private String condition;
    private String plunker;

    @Override
    public void init(Config config) {
        super.init(config);
        compileExpression(condition);
    }

    Expression compileExpression(String expression) {
        // AND takes precedence, so split over AND first
        Matcher m = REGEX_AND.matcher(expression);
        if (m.find()) {
            return new AndExpression()
                    .addExpression(compileExpression(m.group(1)))
                    .addExpression(compileExpression(m.group(3)));
        }

        // OR is next
        m = REGEX_OR.matcher(expression);
        if (m.find()) {
            return new OrExpression()
                    .addExpression(compileExpression(m.group(1)))
                    .addExpression(compileExpression(m.group(3)));
        }

        // the rest is 'normal' expressions
        Matcher expMatcher = REGEX_EXPRESSION.matcher(expression);
        if (!expMatcher.find()) {
            throw new FpqException("could not 'compile' conditional expression, " + expression);
        }

        String var = expMatcher.group(1);
        if ("body".equalsIgnoreCase(var)) {
            return new StringBodyExpression(expMatcher.group(2), expMatcher.group(3));
        }
        else {
            Matcher varMatcher = REGEX_HEADER_VAR.matcher(var);
            if (varMatcher.find()) {
                return new HeaderExpression(varMatcher.group(1), expMatcher.group(2), expMatcher.group(3));
            }
            else {
                throw new FpqException("cannot compile conditional expression, " + expression);
            }
        }
    }

    @Override
    public void shutdown() {
        // nothing to do yet
    }

    @Override
    public PlunkerRunner canRoute(String catcherId, FpqEvent event) {
        if (calculateIncludes(event)) {
            return config.getPlunkers().get(plunker);
        }
        else {
            return null;
        }
    }

    private boolean calculateIncludes(FpqEvent event) {
        return false;
    }

    public String getPlunker() {
        return plunker;
    }

    public void setPlunker(String plunker) {
        this.plunker = plunker;
    }

}

