package net.sourceforge.vrapper.eclipse.platform;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Scanner;
import java.util.Stack;
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
}
class FloatValue implements Value {
	private double value;

	public FloatValue(double value) {
		super();
		this.value = value;
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
}
class DictionaryValue implements Value {
	private Map<String, Value> map = new HashMap<String, Value>();
	public DictionaryValue(DictEntry[] entries) {
		for (DictEntry entry : entries) {
			map.put(entry.getKeyAsString(), entry.getValue());
		}
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
	static Stack<Operations> operations = new Stack<Operations>();
	static LinkedList<Value> operands = new LinkedList<Value>();
	static Stack<Integer> operandsCount = new Stack<Integer>();
	
	public static void parse(String input) {
		while ( ! input.isEmpty()) {
			input = parseIter(input);
		}
	}
	
	private static String parseIter(String input) {
		if (input.startsWith("[")) {
			operations.push(Operations.List);
			pushNewOperandsCount();
			return input.substring(1);
		}
		if (input.startsWith("{")) {
			operations.push(Operations.Dictionary);
			pushNewOperandsCount();
			return input.substring(1);
		}
		if (input.startsWith(":")) {
			operations.push(Operations.DictEntry);
			decrementLastAndPushNew();
			return input.substring(1);
		}
		if (input.startsWith(",")) {
			if (operations.peek() == Operations.DictEntry) {
				handleDictEntry();
			}
			return input.substring(1);
		}
		if (input.startsWith("}")) {
			if (operations.peek() == Operations.DictEntry) {
				handleDictEntry();
			}
			if (operations.pop() != Operations.Dictionary) {
				throw new IllegalStateException("closing \"}\" has not pair.");
			}
			int lastOperandsCount = operandsCount.pop();
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
			if (operations.pop() != Operations.List) {
				throw new IllegalStateException("closing \"]\" has not pair.");
			}
			int lastOperandsCount = operandsCount.pop();
			Value[] items = new Value[lastOperandsCount];
			for (int i=0; i<lastOperandsCount; i++) {
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
		int lastOperandsCount = operandsCount.pop();
		if (lastOperandsCount != 2) {
			throw new IllegalStateException("dictionary entry takes two operands, but found " + lastOperandsCount);
		}
		Value value = operands.removeFirst();
		Value key = operands.removeFirst();
		operands.addFirst(new DictEntry(key, value));
		incrementLastOperandsCount();
		operations.pop();
	}
	private static void pushNewOperandsCount() {
		operandsCount.push(0);
	}
	private static void incrementLastOperandsCount() {
		if (operandsCount.isEmpty()) {
			operandsCount.push(1);
		} else {
			int value = operandsCount.pop();
			operandsCount.push(value + 1);
		}
	}
	private static void decrementLastAndPushNew() {
		int value = operandsCount.pop();
		operandsCount.push(value - 1);
		operandsCount.push(1);
	}
}