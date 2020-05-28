package lucee.extension.util;

import lucee.runtime.PageContext;
import lucee.runtime.exp.PageException;

public interface Functions {

	public static final String SERIALIZE_JSON_CLASS = "lucee.runtime.functions.conversion.SerializeJSON";
	public static final String SERIALIZE_CLASS = "lucee.runtime.functions.dynamicEvaluation.Serialize";
	public static final String EVALUATE_CLASS = "lucee.runtime.functions.dynamicEvaluation.Evaluate";
	public static final String DESERIALIZE_JSON_CLASS = "lucee.runtime.functions.conversion.DeserializeJSON";

	public String serializeJSON(Object var, boolean serializeQueryByColumns) throws PageException;

	public String serializeJSON(PageContext pc, Object var, boolean serializeQueryByColumns) throws PageException;

	public String serialize(Object var) throws PageException;

	public String serialize(PageContext pc, Object var) throws PageException;

	public Object deserializeJSON(String obj) throws PageException;

	public Object deserializeJSON(PageContext pc, String obj) throws PageException;

	public Object evaluate(Object obj) throws PageException;

	public Object evaluate(PageContext pc, Object obj) throws PageException;
}