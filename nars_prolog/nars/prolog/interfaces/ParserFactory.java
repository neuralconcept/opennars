package nars.prolog.interfaces;

import java.util.HashMap;
import nars.prolog.OperatorManager;
import nars.prolog.Parser;
import nars.prolog.Term;

public class ParserFactory {
	
	/**
     * Creating a parser with default operator interpretation
     */
	public static IParser createParser(String theory) {
		return new Parser(theory);
	}
	
	/**
     * creating a parser with default operator interpretation
     */
    public static IParser createParser(String theory, HashMap<Term, Integer> mapping) {
    	return new Parser(theory, mapping);
    }    
	
	/**
     * creating a Parser specifing how to handle operators
     * and what text to parse
     */
    public static IParser createParser(IOperatorManager op, String theory) {
    	return new Parser((OperatorManager)op, theory);
    }
    
    /**
     * creating a Parser specifing how to handle operators
     * and what text to parse
     */
    public static IParser createParser(IOperatorManager op, String theory, HashMap<Term, Integer> mapping) {
    	return new Parser((OperatorManager)op, theory, mapping);
    }

}
