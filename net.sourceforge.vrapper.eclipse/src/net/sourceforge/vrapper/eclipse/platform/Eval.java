package net.sourceforge.vrapper.eclipse.platform;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Eval {
	
	public static void main(String[] args) {
		Scanner in = new Scanner(System.in);
		while (true) {
			String line = in.nextLine();
			
			if ("q".equals(line)) {
				System.out.println("goodbye");
				in.close();
				break;
			}
			
			Pars.parse(line);
			Queue<Value> operands = Pars.operands;
			int s = operands.size();
			System.out.println(s);
			for (Value v : operands) {
				System.out.println(v);
			}
			
			if (line.startsWith(":let")) {
				String input = line.substring(4).trim();
				let(input);
			}
			
			if (line.startsWith(":echo")) {
				String input = line.substring(5).trim();
				String result = echo(input);
				System.out.println(result);
			}
			
		}
	}
	
	private static void let(String input) {
		
	}

	private static String echo(String input) {
		return null;
	}
}

class Memory {
	private static Map<String, Func> funcs = new HashMap<String, Func>();
	private static Map<String, Value> values = new HashMap<String, Value>();
	
	public static Func getReferencedFunc(String name) {
		String funcName;
		if (values.containsKey(name)) {
			Value value = values.get(name);
			if (! (value instanceof Funcref)) {
				throw new IllegalArgumentException("name " + name + " references non-func value");
			}
			Funcref funcref = (Funcref) value;
			funcName = funcref.getFuncName();
		} else {
			funcName = name;
		}
		
		if (!funcs.containsKey(funcName)) {
			throw new IllegalArgumentException("unknown function name \"" + funcName + "\"");
		}
		
		return funcs.get(funcName);
	}
}

interface Value {
	
}

class NumberValue implements Value {
	private int value;
	public NumberValue(int num) {
		this.value = num;
	}
	public int getValue() {
		return value;
	}
	
	public String toString() {
		return String.valueOf(value);
	}
}
class FloatValue implements Value {
	private double value;

	public FloatValue(double value) {
		super();
		this.value = value;
	}
	
	public String toString() {
		return String.valueOf(value);
	}
}
class StringValue implements Value {
	private String value;
	public StringValue(String value) {
		this.value = value;
	}
	public String getValue() {
		return value;
	}
	public String toString() {
		return "\"" + value + "\"";
	}
}
class Funcref implements Value, Expr {
	private Func func;
	private String funcName;
	public Funcref(String funcName) {
		this.funcName = funcName;
		this.func = Memory.getReferencedFunc(funcName);
	}
	public String getFuncName() {
		return funcName;
	}
	public Value eval(Value ... operands) {
		return func.eval(operands);
	}
}
class ListValue implements Value {
	private List<Value> items = new ArrayList<Value>();
	public ListValue(Value ... items) {
		this.items = new ArrayList<Value>(Arrays.asList(items));
	}
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("[");
		Iterator<Value> itemsIt = items.iterator();
		while (itemsIt.hasNext()) {
			sb.append(itemsIt.next());
			if (itemsIt.hasNext()) {
				sb.append(",");
			}
		}
		sb.append("]");
		return sb.toString();
	}
}
class DictionaryValue implements Value {
	private Map<String, Value> map = new HashMap<String, Value>();
	public DictionaryValue(DictEntry[] entries) {
		for (DictEntry entry : entries) {
			map.put(entry.getKeyAsString(), entry.getValue());
		}
	}
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("{");
		Iterator<Map.Entry<String,Value>> it = map.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<String, Value> entry = it.next();
			sb.append(entry.getKey());
			sb.append(":");
			sb.append(entry.getValue());
			if (it.hasNext()) {
				sb.append(",");
			}
		}
		sb.append("}");
		return sb.toString();
	}
}
class DictEntry implements Value {
	private StringValue key;
	private Value value;
	public DictEntry(Value key, Value value) {
		if (key instanceof NumberValue) {
			String numAsString = String.valueOf(((NumberValue)key).getValue());
			this.key = new StringValue(numAsString);
		} else if (key instanceof StringValue) {
			this.key = (StringValue) key;
		} else {
			throw new IllegalArgumentException("illegal key type. 'string' and 'number' are allowed.");
		}
		this.value = value;
	}
	public StringValue getKey() {
		return key;
	}
	public String getKeyAsString() {
		return key.getValue();
	}
	public Value getValue() {
		return value;
	}
}

interface Expr {
	Value eval(Value ... operands);
}

abstract class Func implements Expr {
	public abstract Value eval(Value ... operands);
}

/*class ListExpr implements Expr {
	public Value eval(Value ... operands) {
		
	}
}*/

class Pars {
	static enum Operations {Funcref, List, Dictionary, DictEntry, Plus, Equals, Point}
	static LinkedList<Operations> operations = new LinkedList<Operations>();
	static LinkedList<Value> operands = new LinkedList<Value>();
	static LinkedList<Integer> operandsCount = new LinkedList<Integer>();
	
	public static void parse(String input) {
		while ( ! input.isEmpty()) {
			input = parseIter(input);
		}
	}
	
	private static String parseIter(String input) {
		if (input.startsWith("[")) {
			operations.addFirst(Operations.List);
			pushNewOperandsCount();
			return input.substring(1);
		}
		if (input.startsWith("{")) {
			operations.addFirst(Operations.Dictionary);
			pushNewOperandsCount();
			return input.substring(1);
		}
		if (input.startsWith(":")) {
			operations.addFirst(Operations.DictEntry);
			decrementLastAndPushNew();
			return input.substring(1);
		}
		if (input.startsWith(",")) {
			if (operations.peekFirst() == Operations.DictEntry) {
				handleDictEntry();
			}
			return input.substring(1);
		}
		if (input.startsWith("}")) {
			if (operations.peekFirst() == Operations.DictEntry) {
				handleDictEntry();
			}
			if (operations.pollFirst() != Operations.Dictionary) {
				throw new IllegalStateException("closing \"}\" has not pair.");
			}
			int lastOperandsCount = operandsCount.pollFirst();
			DictEntry[] entries = new DictEntry[lastOperandsCount];
			for (int i=0; i<lastOperandsCount; i++) {
				entries[i] = (DictEntry) operands.removeFirst();
			}
			DictionaryValue value = new DictionaryValue(entries);
			operands.addFirst(value);
			incrementLastOperandsCount();
			return input.substring(1);
		}
		if (input.startsWith("]")) {
			if (operations.pollFirst() != Operations.List) {
				throw new IllegalStateException("closing \"]\" has not pair.");
			}
			int lastOperandsCount = operandsCount.pollFirst();
			Value[] items = new Value[lastOperandsCount];
			for (int i=lastOperandsCount-1; i>=0; i--) {
				items[i] = operands.removeFirst();
			}
			ListValue value = new ListValue(items);
			operands.addFirst(value);
			incrementLastOperandsCount();
			return input.substring(1);
		}

		Pattern floatPattern = Pattern.compile("(-?\\d+\\.\\d+(e-?\\d+)?)(.*)");
		Matcher floatMatcher = floatPattern.matcher(input);
		if (floatMatcher.matches()) {
			double value = Double.parseDouble(floatMatcher.group(1));
			operands.addFirst(new FloatValue(value));
			incrementLastOperandsCount();
			return floatMatcher.group(floatMatcher.groupCount());
		}

		Pattern hexPattern = Pattern.compile("^(-?)0x([0-9a-fA-F]+)(.*)");
		Matcher hexMatcher = hexPattern.matcher(input);
		Pattern octPattern = Pattern.compile("^(-?)0([0-7]+)(.*)");
		Matcher octMatcher = octPattern.matcher(input);
		Pattern decPattern = Pattern.compile("^-?(\\d+)(.*)");
		Matcher decMatcher = decPattern.matcher(input);
		if (hexMatcher.matches()) {
			int num = Integer.parseInt(hexMatcher.group(1) + hexMatcher.group(2), 16);
			operands.addFirst(new NumberValue(num));
			incrementLastOperandsCount();
			return hexMatcher.group(hexMatcher.groupCount());
		} else if (octMatcher.matches()) {
			int num = Integer.parseInt(octMatcher.group(1) + octMatcher.group(2), 8);
			operands.addFirst(new NumberValue(num));
			incrementLastOperandsCount();
			return octMatcher.group(octMatcher.groupCount());
		} else if (decMatcher.matches()) {
			int num = Integer.parseInt(decMatcher.group(1));
			operands.addFirst(new NumberValue(num));
			incrementLastOperandsCount();
			return decMatcher.group(decMatcher.groupCount());
		}
		
		Pattern stringPattern = Pattern.compile("(\"([^\"]*)\"|'([^']*)')(.*)");
		Matcher stringMatcher = stringPattern.matcher(input);
		if (stringMatcher.matches()) {
			String value = stringMatcher.group(1);
			value = value.substring(1, value.length()-1);
			operands.addFirst(new StringValue(value));
			incrementLastOperandsCount();
			return stringMatcher.group(stringMatcher.groupCount());
		}	

		throw new IllegalAccessError("bad input. \"" + input +"\" didn't match anything");
	}
	private static void handleDictEntry() {
		int lastOperandsCount = operandsCount.pollFirst();
		if (lastOperandsCount != 2) {
			throw new IllegalStateException("dictionary entry takes two operands, but found " + lastOperandsCount);
		}
		Value value = operands.removeFirst();
		Value key = operands.removeFirst();
		operands.addFirst(new DictEntry(key, value));
		incrementLastOperandsCount();
		operations.pollFirst();
	}
	private static void pushNewOperandsCount() {
		operandsCount.addFirst(0);
	}
	private static void incrementLastOperandsCount() {
		if (operandsCount.isEmpty()) {
			operandsCount.addFirst(1);
		} else {
			operandsCount.set(0, operandsCount.peekFirst() + 1);
		}
	}
	private static void decrementLastAndPushNew() {
		operandsCount.set(0, operandsCount.peekFirst() - 1);
		operandsCount.addFirst(1);
	}
}