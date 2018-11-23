package lucee.extension.util;

import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.runtime.PageContext;
import lucee.runtime.exp.PageException;
import lucee.runtime.ext.function.BIF;
import lucee.runtime.util.Cast;

public class FunctionsModern implements Functions {

    private final CFMLEngine engine;

    public FunctionsModern() {
	engine = CFMLEngineFactory.getInstance();
    }

    @Override
    public String serializeJSON(Object var, boolean serializeQueryByColumns) throws PageException {
	return serializeJSON(pc(), var, serializeQueryByColumns);
    }

    @Override
    public String serializeJSON(PageContext pc, Object var, boolean serializeQueryByColumns) throws PageException {
	Cast caster = engine.getCastUtil();
	try {
	    BIF bif = engine.getClassUtil().loadBIF(pc, SERIALIZE_JSON_CLASS);
	    return caster.toString(bif.invoke(pc, new Object[] { var, serializeQueryByColumns }));
	}
	catch (Exception e) {
	    throw caster.toPageException(e);
	}
    }

    @Override
    public String serialize(Object var) throws PageException {
	return serialize(pc(), var);
    }

    @Override
    public String serialize(PageContext pc, Object var) throws PageException {
	Cast caster = engine.getCastUtil();
	try {
	    BIF bif = engine.getClassUtil().loadBIF(pc, SERIALIZE_CLASS);
	    return caster.toString(bif.invoke(pc, new Object[] { var }));
	}
	catch (Exception e) {
	    throw caster.toPageException(e);
	}
    }

    @Override
    public Object deserializeJSON(String obj) throws PageException {
	return deserializeJSON(pc(), obj);
    }

    @Override
    public Object deserializeJSON(PageContext pc, String obj) throws PageException {
	Cast caster = engine.getCastUtil();
	try {
	    BIF bif = engine.getClassUtil().loadBIF(pc, DESERIALIZE_JSON_CLASS);
	    return bif.invoke(pc, new Object[] { obj });
	}
	catch (Exception e) {
	    throw caster.toPageException(e);
	}
    }

    private PageContext pc() {
	return engine.getThreadPageContext();
    }

    @Override
    public Object evaluate(Object obj) throws PageException {
	return evaluate(pc(), obj);
    }

    @Override
    public Object evaluate(PageContext pc, Object obj) throws PageException {
	Cast caster = engine.getCastUtil();
	try {
	    BIF bif = engine.getClassUtil().loadBIF(pc, EVALUATE_CLASS);
	    return bif.invoke(pc, new Object[] { new Object[] { obj } });
	}
	catch (Exception e) {
	    throw caster.toPageException(e);
	}
    }
}
