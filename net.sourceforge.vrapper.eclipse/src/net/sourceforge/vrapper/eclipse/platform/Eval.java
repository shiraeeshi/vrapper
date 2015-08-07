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
			Queue<Expr> operands = Pars.operands;
			int s = operands.size();
			System.out.println(s);
			for (Expr v : operands) {
				System.out.println(v.eval());
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

interface Value {}

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
	public static NumberValue fromString(StringValue stringValue) {
		String str = stringValue.getValue();
		Pattern hexPattern = Pattern.compile("^(-?)0x([0-9a-fA-F]+)(.*)"); // TODO code duplication
		Matcher hexMatcher = hexPattern.matcher(str);
		Pattern octPattern = Pattern.compile("^(-?)0([0-7]+)(.*)");
		Matcher octMatcher = octPattern.matcher(str);
		Pattern decPattern = Pattern.compile("^-?(\\d+)(.*)");
		Matcher decMatcher = decPattern.matcher(str);
		if (hexMatcher.matches()) {
			int num = Integer.parseInt(hexMatcher.group(1) + hexMatcher.group(2), 16);
			return new NumberValue(num);
		}
		if (octMatcher.matches()) {
			int num = Integer.parseInt(octMatcher.group(1) + octMatcher.group(2), 8);
			return new NumberValue(num);
		}
		if (decMatcher.matches()) {
			int num = Integer.parseInt(decMatcher.group(1));
			return new NumberValue(num);
		}
		return new NumberValue(0);
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
	public Value eval() {
		return func.eval();
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

class LogicalValue implements Expr, NonTernaryExpr {
	private static enum LogicalOp {OR,AND}
	public static LogicalOp OR = LogicalOp.OR;
	public static LogicalOp AND = LogicalOp.AND;
	private LinkedList<Expr> operands = new LinkedList<Expr>();

	private class ANDOperandsChain implements Expr {
		private LinkedList<Expr> andOperands = new LinkedList<Expr>();
		public ANDOperandsChain(Expr leftmost, Expr second) {
			andOperands.addLast(leftmost);
			andOperands.addLast(second);
		}
		public void addOperand(Expr operand) {
			andOperands.addLast(operand);
		}
		public Value eval() {
			if (andOperands.isEmpty()) {
				throw new IllegalStateException("cannot evaluate empty andOperandsChain.");
			}
			while ( ! andOperands.isEmpty()) {
				if ( ! evaluatesToNonZero(andOperands.pollFirst().eval())) {
					return FALSE();
				}
			}
			return TRUE();
		}
	}
	
	public LogicalValue(Expr leftmostOperand) {
		operands.addLast(leftmostOperand);
	}

	public void appendOperation(LogicalOp op, Expr operand) {
		if (op == OR) {
			operands.addLast(operand);
		} else {
			if (operands.peekLast() instanceof ANDOperandsChain) {
				((ANDOperandsChain)operands.peekLast()).addOperand(operand);
			} else {
				operands.addLast(new ANDOperandsChain(operands.pollLast(), operand));
			}
		}
	}
	
	public Value eval() {
		if (operands.isEmpty()) throw new IllegalStateException("cannot evaluate empty (no operands) LogicalValue.");
		while ( ! operands.isEmpty()) {
			if (evaluatesToNonZero(operands.pollFirst().eval())) {
				return TRUE();
			}
		}
		return FALSE();
	}
	
	
	private NumberValue FALSE() {
		return new NumberValue(0);
	}

	private NumberValue TRUE() {
		return new NumberValue(1); // any non-zero number
	}

	public static boolean evaluatesToNonZero(Value expr) {
		if (expr instanceof FloatValue) {
			throw new IllegalArgumentException("Using Float as a Number");
		}
		if (expr instanceof StringValue) {
			NumberValue converted = NumberValue.fromString((StringValue)expr);
			return converted.getValue() != 0;
		}
		if (expr instanceof NumberValue) {
			return ((NumberValue)expr).getValue() != 0;
		}
		throw new IllegalArgumentException("Illegal value");
	}
	
}

class TernaryValue implements Expr {
	private NonTernaryExpr first;
	private Expr second;
	private Expr third;

	public TernaryValue(NonTernaryExpr first, Expr second, Expr third) {
		this.first = first;
		this.second = second;
		this.third = third;
	}
	@Override
	public Value eval() {
		return LogicalValue.evaluatesToNonZero(first.eval()) ?
				second.eval() : third.eval();
	}

	public void changeThird(Expr second2, Expr third2) {
		if (this.third instanceof TernaryValue) {
			((TernaryValue) this.third).changeThird(second2, third2);
		} else {
			this.third = new TernaryValue((NonTernaryExpr) this.third, second2, third2);
		}
	}
	
}

class ValueWrapperExpr implements Expr, NonTernaryExpr {
	private Value value;

	public ValueWrapperExpr(Value value) {
		this.value = value;
	}

	public Value eval() {
		return value;
	}
}

interface Expr {
	Value eval();
}

interface NonTernaryExpr extends Expr {}

abstract class Func {
	public abstract Value eval(Value ... operands);
}

/*class ListExpr implements Expr {
	public Value eval(Value ... operands) {
		
	}
}*/

class Pars {
	static enum Operations {Funcref, List, Dictionary, DictEntry, Plus, Equals, Point,
		TernaryQuestionMark, TernaryColon,
		LogicalOR, LogicalAND}
	static LinkedList<Operations> operations = new LinkedList<Operations>();
	static LinkedList<Expr> operands = new LinkedList<Expr>();
	static LinkedList<Integer> operandsCount = new LinkedList<Integer>();
	
	public static void parse(String input) {
		operations = new LinkedList<Operations>();
		operands = new LinkedList<Expr>();
		operandsCount = new LinkedList<Integer>();
		while ( ! input.isEmpty()) {
			input = parseIter(input);
		}
	}
	
	private static Pattern mainPattern = Pattern.compile("\\s*"
				+ "("
				+ "(\\[)" // group 2 left square bracket
				+ "|(\\{)" // group 3 left curly bracket
				+ "|(:)" // group 4 colon
				+ "|([,])" // group 5 comma
				+ "|(\\})" // group 6 right curly bracket
				+ "|(\\])" // group 7 right square bracket
				+ "|(-?\\d+\\.\\d+(e-?\\d+)?)" // group 8,9 float
				+ "|((-?)0x([0-9a-fA-F]+))"// group 10,11,12 hex
				+ "|((-?)0([0-7]+))"// group 13,14,15 octal
				+ "|(-?(\\d+))"// group 16,17 decimal
				+ "|(\"([^\"]*)\"|'([^']*)')"// group 18,19,20 string
				+ "|(\\&\\&|\\|\\|)"// group 21 logical
				+ "|(\\?)"// group 22 question mark
				+ ")"
				+ "\\s*(.*)");

	private static String parseIter(String input) {
		Matcher mainMatcher = mainPattern.matcher(input);
		if ( ! mainMatcher.matches()) {
			throw new IllegalStateException("main matcher doesn't match");
		}
		boolean isLeftSquareBracket = mainMatcher.group(2) != null;
		if (isLeftSquareBracket) {
			operations.addFirst(Operations.List);
			pushNewOperandsCount();
			return mainMatcher.group(mainMatcher.groupCount());
		}
		boolean isLeftCurlyBracket = mainMatcher.group(3) != null;
		if (isLeftCurlyBracket) {
			operations.addFirst(Operations.Dictionary);
			pushNewOperandsCount();
			return mainMatcher.group(mainMatcher.groupCount());
		}
		boolean isColon = mainMatcher.group(4) != null;
		if (isColon) {
			if (operations.peekFirst() == Operations.TernaryQuestionMark) {
				operations.addFirst(Operations.TernaryColon);
			}
			else if (operations.peekFirst() == Operations.Dictionary) {
				operations.addFirst(Operations.DictEntry);
				decrementLastAndPushNew();
			}
			else {
				throw new IllegalArgumentException("illegal colon position");
			}
			return mainMatcher.group(mainMatcher.groupCount());
		}
		boolean isComma = mainMatcher.group(5) != null;
		if (isComma) {
			if (operations.peekFirst() == Operations.DictEntry) {
				handleDictEntry();
			}
			return mainMatcher.group(mainMatcher.groupCount());
		}
		boolean isRightCurlyBracket = mainMatcher.group(6) != null;
		if (isRightCurlyBracket) {
			if (operations.peekFirst() == Operations.DictEntry) {
				handleDictEntry();
			}
			if (operations.pollFirst() != Operations.Dictionary) {
				throw new IllegalStateException("closing \"}\" has not pair.");
			}
			int lastOperandsCount = operandsCount.pollFirst();
			DictEntry[] entries = new DictEntry[lastOperandsCount];
			for (int i=0; i<lastOperandsCount; i++) {
				entries[i] = (DictEntry) operands.removeFirst().eval();
			}
			DictionaryValue value = new DictionaryValue(entries);
			addNewOperand(value);
			return mainMatcher.group(mainMatcher.groupCount());
		}
		boolean isRightSquareBracket = mainMatcher.group(7) != null;
		if (isRightSquareBracket) {
			if (operations.pollFirst() != Operations.List) {
				throw new IllegalStateException("closing \"]\" has not pair.");
			}
			int lastOperandsCount = operandsCount.pollFirst();
			Value[] items = new Value[lastOperandsCount];
			for (int i=lastOperandsCount-1; i>=0; i--) {
				items[i] = operands.removeFirst().eval();
			}
			ListValue value = new ListValue(items);
			addNewOperand(value);
			return mainMatcher.group(mainMatcher.groupCount());
		}

		boolean isFloat = mainMatcher.group(8) != null;
		if (isFloat) {
			double value = Double.parseDouble(mainMatcher.group(8));
			addNewOperand(new FloatValue(value));
			return mainMatcher.group(mainMatcher.groupCount());
		}

		boolean isHexadecimal = mainMatcher.group(10) != null;
		boolean isOctal = mainMatcher.group(13) != null;
		boolean isDecimal = mainMatcher.group(16) != null;
		if (isHexadecimal) {
			int num = Integer.parseInt(mainMatcher.group(11) + mainMatcher.group(12), 16);
			addNewOperand(new NumberValue(num));
			return mainMatcher.group(mainMatcher.groupCount());
		} else if (isOctal) {
			int num = Integer.parseInt(mainMatcher.group(14) + mainMatcher.group(15), 8);
			addNewOperand(new NumberValue(num));
			return mainMatcher.group(mainMatcher.groupCount());
		} else if (isDecimal) {
			int num = Integer.parseInt(mainMatcher.group(16));
			addNewOperand(new NumberValue(num));
			return mainMatcher.group(mainMatcher.groupCount());
		}
		
		boolean isString = mainMatcher.group(18) != null;
		if (isString) {
			String value = mainMatcher.group(18);
			value = value.substring(1, value.length()-1);
			addNewOperand(new StringValue(value));
			return mainMatcher.group(mainMatcher.groupCount());
		}
		
		// afterstringpattern:
		boolean isLogical = mainMatcher.group(21) != null;
		if (isLogical) {
			String opString = mainMatcher.group(21);
			if ("&&".equals(opString)) {
				operations.addFirst(Operations.LogicalAND);
			}
			else if ("||".equals(opString)) {
				operations.addFirst(Operations.LogicalOR);
			}
			else {
				throw new IllegalStateException("illegal logical ( '||' | '&&' ) operation " + opString);
			}
			if ( ! (operands.peekFirst() instanceof LogicalValue)) {
				Expr leftmostOperand = operands.pollFirst();
				operands.addFirst(new LogicalValue(leftmostOperand));
				int lastOperandsCount = operandsCount.pollFirst();
				if (lastOperandsCount != 1) {
					throw new IllegalStateException(
							"logical expression expects single operand when initialized, but found " + lastOperandsCount);
				}
				incrementLastOperandsCount();
			}
			return mainMatcher.group(mainMatcher.groupCount());
		}
		
		boolean isQuestionMark = mainMatcher.group(22) != null;
		if (isQuestionMark) {
			if (operands.peekFirst() instanceof TernaryValue) {
				pushNewOperandsCount();
				incrementLastOperandsCount();
			} else {
				decrementLastAndPushNew();
			}
			operations.addFirst(Operations.TernaryQuestionMark);
			return mainMatcher.group(mainMatcher.groupCount());
		}

		throw new IllegalAccessError("bad input. \"" + input +"\" didn't match anything");
	}
	private static void addNewOperand(Value operand) {
		addNewOperand(new ValueWrapperExpr(operand));
	}
	private static void addNewOperand(Expr operand) {
		if (operations.peekFirst() == Operations.LogicalOR ||
				operations.peekFirst() == Operations.LogicalAND) {
			if ( ! (operands.peekFirst() instanceof LogicalValue)) {
				throw new IllegalStateException("logical operation without last LogicalValue-type operand");
			}
			LogicalValue logicalChain = (LogicalValue)operands.peekFirst();
			if (operations.pollFirst() == Operations.LogicalOR) {
				logicalChain.appendOperation(LogicalValue.OR, operand);
			}
			else if (operations.pollFirst() == Operations.LogicalAND) {
				logicalChain.appendOperation(LogicalValue.AND, operand);
			}
			return;
		}
		operands.addFirst(operand);
		incrementLastOperandsCount();

		if (operations.peekFirst() == Operations.TernaryColon) {
			handleTernary();
		}
	}

	private static void handleTernary() {
		operations.removeFirst(); // remove colon
		operations.removeFirst(); // remove question mark
		int lastOperandsCount = operandsCount.pollFirst();
		if (lastOperandsCount != 3) {
			throw new IllegalStateException("ternary expression takes 3 operands, but found " + lastOperandsCount);
		}
		Expr ternaryExpr;
		Expr third = operands.removeFirst();
		Expr second = operands.removeFirst();
		Expr first = operands.removeFirst();
		if (first instanceof TernaryValue) {
			TernaryValue firstTernary = (TernaryValue) first;
			firstTernary.changeThird(second, third);
			operands.addFirst(firstTernary);
			return;
		}
		ternaryExpr = new TernaryValue((NonTernaryExpr) first, second, third);
		addNewOperand(ternaryExpr);
	}
	private static void handleDictEntry() {
		int lastOperandsCount = operandsCount.pollFirst();
		if (lastOperandsCount != 2) {
			throw new IllegalStateException("dictionary entry takes two operands, but found " + lastOperandsCount);
		}
		Value value = operands.removeFirst().eval();
		Value key = operands.removeFirst().eval();
		DictEntry dictEntry = new DictEntry(key, value);
		operands.addFirst(new ValueWrapperExpr(dictEntry));
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