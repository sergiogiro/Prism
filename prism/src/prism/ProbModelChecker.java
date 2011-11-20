//==============================================================================
//	
//	Copyright (c) 2002-
//	Authors:
//	* Dave Parker <david.parker@comlab.ox.ac.uk> (University of Oxford, formerly University of Birmingham)
//	
//------------------------------------------------------------------------------
//	
//	This file is part of PRISM.
//	
//	PRISM is free software; you can redistribute it and/or modify
//	it under the terms of the GNU General Public License as published by
//	the Free Software Foundation; either version 2 of the License, or
//	(at your option) any later version.
//	
//	PRISM is distributed in the hope that it will be useful,
//	but WITHOUT ANY WARRANTY; without even the implied warranty of
//	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//	GNU General Public License for more details.
//	
//	You should have received a copy of the GNU General Public License
//	along with PRISM; if not, write to the Free Software Foundation,
//	Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//	
//==============================================================================

package prism;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Vector;

import jdd.*;
import jltl2dstar.DRA;
import jltl2dstar.LTL2Rabin;
import dv.*;
import mtbdd.*;
import sparse.*;
import hybrid.*;
import parser.ast.*;
import parser.visitor.ASTTraverse;

/*
 * Model checker for DTMCs.
 */
public class ProbModelChecker extends NonProbModelChecker
{
	// Model (DTMC or CTMC)
	protected ProbModel model;

	// Options (in addition to those inherited from StateModelChecker):

	// Use 0,1 precomputation algorithms?
	protected boolean precomp;
	protected boolean prob0;
	protected boolean prob1;
	// Do BSCC computation?
	protected boolean bsccComp;

	// Constructor

	public ProbModelChecker(Prism prism, Model m, PropertiesFile pf) throws PrismException
	{
		// Initialise
		super(prism, m, pf);
		if (!(m instanceof ProbModel)) {
			throw new PrismException("Wrong model type passed to ProbModelChecker.");
		}
		model = (ProbModel) m;

		// Inherit some options from parent Prism object.
		// Store locally and/or pass onto engines.
		precomp = prism.getPrecomp();
		prob0 = prism.getProb0();
		prob1 = prism.getProb1();
		bsccComp = prism.getBSCCComp();
		switch (engine) {
		case Prism.MTBDD:
			PrismMTBDD.setLinEqMethod(prism.getLinEqMethod());
			PrismMTBDD.setLinEqMethodParam(prism.getLinEqMethodParam());
			PrismMTBDD.setTermCrit(prism.getTermCrit());
			PrismMTBDD.setTermCritParam(prism.getTermCritParam());
			PrismMTBDD.setMaxIters(prism.getMaxIters());
			PrismMTBDD.setDoSSDetect(prism.getDoSSDetect());
			break;
		case Prism.SPARSE:
			PrismSparse.setLinEqMethod(prism.getLinEqMethod());
			PrismSparse.setLinEqMethodParam(prism.getLinEqMethodParam());
			PrismSparse.setTermCrit(prism.getTermCrit());
			PrismSparse.setTermCritParam(prism.getTermCritParam());
			PrismSparse.setMaxIters(prism.getMaxIters());
			PrismSparse.setCompact(prism.getCompact());
			PrismSparse.setDoSSDetect(prism.getDoSSDetect());
		case Prism.HYBRID:
			PrismHybrid.setLinEqMethod(prism.getLinEqMethod());
			PrismHybrid.setLinEqMethodParam(prism.getLinEqMethodParam());
			PrismHybrid.setTermCrit(prism.getTermCrit());
			PrismHybrid.setTermCritParam(prism.getTermCritParam());
			PrismHybrid.setMaxIters(prism.getMaxIters());
			PrismHybrid.setCompact(prism.getCompact());
			PrismHybrid.setSBMaxMem(prism.getSBMaxMem());
			PrismHybrid.setNumSBLevels(prism.getNumSBLevels());
			PrismHybrid.setSORMaxMem(prism.getSORMaxMem());
			PrismHybrid.setNumSORLevels(prism.getNumSORLevels());
			PrismHybrid.setDoSSDetect(prism.getDoSSDetect());
		}
	}

	// Override-able "Constructor"

	public ProbModelChecker createNewModelChecker(Prism prism, Model m, PropertiesFile pf) throws PrismException
	{
		return new ProbModelChecker(prism, m, pf);
	}

	// -----------------------------------------------------------------------------------
	// Check a property, i.e. an expression
	// -----------------------------------------------------------------------------------

	// Check expression (recursive)

	public StateValues checkExpression(Expression expr) throws PrismException
	{
		StateValues res;

		// P operator
		if (expr instanceof ExpressionProb) {
			res = checkExpressionProb((ExpressionProb) expr);
		}
		// R operator
		else if (expr instanceof ExpressionReward) {
			res = checkExpressionReward((ExpressionReward) expr);
		}
		// S operator
		else if (expr instanceof ExpressionSS) {
			res = checkExpressionSteadyState((ExpressionSS) expr);
		}
		// Otherwise, use the superclass
		else {
			res = super.checkExpression(expr);
		}

		// Filter out non-reachable states from solution
		// (only necessary for symbolically stored vectors)
		if (res instanceof StateValuesMTBDD)
			res.filter(reach);

		return res;
	}

	// -----------------------------------------------------------------------------------
	// Check method for each operator
	// -----------------------------------------------------------------------------------

	// P operator

	protected StateValues checkExpressionProb(ExpressionProb expr) throws PrismException
	{
		Expression pb; // probability bound (expression)
		double p = 0; // probability bound (actual value)
		String relOp; // relational operator

		JDDNode sol;
		StateValues probs = null;

		// Get info from prob operator
		relOp = expr.getRelOp();
		pb = expr.getProb();
		if (pb != null) {
			p = pb.evaluateDouble(constantValues);
			if (p < 0 || p > 1)
				throw new PrismException("Invalid probability bound " + p + " in P operator");
		}

		// Check for trivial (i.e. stupid) cases
		if (pb != null) {
			if ((p == 0 && relOp.equals(">=")) || (p == 1 && relOp.equals("<="))) {
				mainLog.printWarning("Checking for probability " + relOp + " " + p
						+ " - formula trivially satisfies all states");
				JDD.Ref(reach);
				return new StateValuesMTBDD(reach, model);
			} else if ((p == 0 && relOp.equals("<")) || (p == 1 && relOp.equals(">"))) {
				mainLog.printWarning("Checking for probability " + relOp + " " + p
						+ " - formula trivially satisfies no states");
				return new StateValuesMTBDD(JDD.Constant(0), model);
			}
		}

		// Print a warning if Pmin/Pmax used
		if (relOp.equals("min=") || relOp.equals("max=")) {
			mainLog.printWarning("\"Pmin=?\" and \"Pmax=?\" operators are identical to \"P=?\" for DTMCs/CTMCs");
		}

		// Compute probabilities
		boolean qual = pb != null && ((p == 0) || (p == 1)) && precomp && prob0 && prob1;
		probs = checkProbPathFormula(expr.getExpression(), qual);

		// Print out probabilities
		if (prism.getVerbose()) {
			mainLog.print("\nProbabilities (non-zero only) for all states:\n");
			probs.print(mainLog);
		}

		// For =? properties, just return values
		if (pb == null) {
			return probs;
		}
		// Otherwise, compare against bound to get set of satisfying states
		else {
			sol = probs.getBDDFromInterval(relOp, p);
			// remove unreachable states from solution
			JDD.Ref(reach);
			sol = JDD.And(sol, reach);
			// free vector
			probs.clear();
			return new StateValuesMTBDD(sol, model);
		}
	}

	// R operator

	protected StateValues checkExpressionReward(ExpressionReward expr) throws PrismException
	{
		Object rs; // reward struct index
		Expression rb; // reward bound (expression)
		double r = 0; // reward bound (actual value)
		String relOp; // relational operator
		Expression expr2; // expression

		JDDNode stateRewards = null, transRewards = null, sol;
		StateValues rewards = null;
		int i;

		// get info from reward operator
		rs = expr.getRewardStructIndex();
		relOp = expr.getRelOp();
		rb = expr.getReward();
		if (rb != null) {
			r = rb.evaluateDouble(constantValues);
			if (r < 0)
				throw new PrismException("Invalid reward bound " + r + " in R[] formula");
		}

		// get reward info
		if (model.getNumRewardStructs() == 0)
			throw new PrismException("Model has no rewards specified");
		if (rs == null) {
			stateRewards = model.getStateRewards(0);
			transRewards = model.getTransRewards(0);
		} else if (rs instanceof Expression) {
			i = ((Expression) rs).evaluateInt(constantValues);
			rs = new Integer(i); // for better error reporting below
			stateRewards = model.getStateRewards(i - 1);
			transRewards = model.getTransRewards(i - 1);
		} else if (rs instanceof String) {
			stateRewards = model.getStateRewards((String) rs);
			transRewards = model.getTransRewards((String) rs);
		}
		if (stateRewards == null || transRewards == null)
			throw new PrismException("Invalid reward structure index \"" + rs + "\"");

		// check for trivial (i.e. stupid) cases
		if (rb != null) {
			if (r == 0 && relOp.equals(">=")) {
				mainLog.printWarning("Checking for reward " + relOp + " " + r
						+ " - formula trivially satisfies all states");
				JDD.Ref(reach);
				return new StateValuesMTBDD(reach, model);
			} else if (r == 0 && relOp.equals("<")) {
				mainLog.printWarning("Checking for reward " + relOp + " " + r
						+ " - formula trivially satisfies no states");
				return new StateValuesMTBDD(JDD.Constant(0), model);
			}
		}

		// print a warning if Rmin/Rmax used
		if (relOp.equals("min=") || relOp.equals("max=")) {
			mainLog.printWarning("\"Rmin=?\" and \"Rmax=?\" operators are identical to \"R=?\" for DTMCs/CTMCs");
		}

		// compute rewards
		expr2 = expr.getExpression();
		if (expr2 instanceof ExpressionTemporal) {
			switch (((ExpressionTemporal) expr2).getOperator()) {
			case ExpressionTemporal.R_C:
				rewards = checkRewardCumul((ExpressionTemporal) expr2, stateRewards, transRewards);
				break;
			case ExpressionTemporal.R_I:
				rewards = checkRewardInst((ExpressionTemporal) expr2, stateRewards, transRewards);
				break;
			case ExpressionTemporal.R_F:
				rewards = checkRewardReach((ExpressionTemporal) expr2, stateRewards, transRewards);
				break;
			case ExpressionTemporal.R_S:
				rewards = checkRewardSS((ExpressionTemporal) expr2, stateRewards, transRewards);
				break;
			}
		}
		if (rewards == null)
			throw new PrismException("Unrecognised operator in R operator");

		// print out rewards
		if (prism.getVerbose()) {
			mainLog.print("\nRewards (non-zero only) for all states:\n");
			rewards.print(mainLog);
		}

		// For =? properties, just return values
		if (rb == null) {
			return rewards;
		}
		// Otherwise, compare against bound to get set of satisfying states
		else {
			sol = rewards.getBDDFromInterval(relOp, r);
			// remove unreachable states from solution
			JDD.Ref(reach);
			sol = JDD.And(sol, reach);
			// free vector
			rewards.clear();
			return new StateValuesMTBDD(sol, model);
		}
	}

	// S operator

	protected StateValues checkExpressionSteadyState(ExpressionSS expr) throws PrismException
	{
		Expression pb; // probability bound (expression)
		double p = 0; // probability bound (actual value)
		String relOp; // relational operator

		// bscc stuff
		Vector<JDDNode> vectBSCCs;
		JDDNode notInBSCCs;
		// mtbdd stuff
		JDDNode b, bscc, sol, tmp;
		// other stuff
		StateValues probs = null, totalProbs = null;
		int i, n;
		double d, probBSCCs[];

		// get info from steady-state operator
		relOp = expr.getRelOp();
		pb = expr.getProb();
		if (pb != null) {
			p = pb.evaluateDouble(constantValues);
			if (p < 0 || p > 1)
				throw new PrismException("Invalid probability bound " + p + " in S operator");
		}

		// check for trivial (i.e. stupid) cases
		if (pb != null) {
			if ((p == 0 && relOp.equals(">=")) || (p == 1 && relOp.equals("<="))) {
				mainLog.printWarning("Checking for probability " + relOp + " " + p
						+ " - formula trivially satisfies all states");
				JDD.Ref(reach);
				return new StateValuesMTBDD(reach, model);
			} else if ((p == 0 && relOp.equals("<")) || (p == 1 && relOp.equals(">"))) {
				mainLog.printWarning("Checking for probability " + relOp + " " + p
						+ " - formula trivially satisfies no states");
				return new StateValuesMTBDD(JDD.Constant(0), model);
			}
		}

		// model check argument
		b = checkExpressionDD(expr.getExpression());

		// compute bottom strongly connected components (bsccs)
		if (bsccComp) {
			SCCComputer sccComputer = prism.getSCCComputer(model);
			sccComputer.computeBSCCs();
			vectBSCCs = sccComputer.getVectBSCCs();
			notInBSCCs = sccComputer.getNotInBSCCs();
			n = vectBSCCs.size();
		}
		// unless we've been told to skip it
		else {
			mainLog.println("\nSkipping BSCC computation...");
			vectBSCCs = new Vector<JDDNode>();
			JDD.Ref(reach);
			vectBSCCs.add(reach);
			notInBSCCs = JDD.Constant(0);
			n = 1;
		}

		// compute steady state for each bscc...
		probBSCCs = new double[n];
		for (i = 0; i < n; i++) {

			mainLog.println("\nComputing steady state probabilities for BSCC " + (i + 1));

			// get bscc
			bscc = vectBSCCs.elementAt(i);

			// compute steady state probabilities
			try {
				probs = computeSteadyStateProbs(trans, bscc);
			} catch (PrismException e) {
				JDD.Deref(b);
				for (i = 0; i < n; i++) {
					JDD.Deref(vectBSCCs.elementAt(i));
				}
				JDD.Deref(notInBSCCs);
				throw e;
			}

			// print out probabilities
			if (verbose) {
				mainLog.print("\nBSCC " + (i + 1) + " steady-state probabilities: \n");
				probs.print(mainLog);
			}

			// sum probabilities over bdd b
			d = probs.sumOverBDD(b);
			probBSCCs[i] = d;
			mainLog.print("\nBSCC " + (i + 1) + " probability: " + d + "\n");

			// free vector
			probs.clear();
		}

		// if every state is in a bscc, it's much easier...
		if (notInBSCCs.equals(JDD.ZERO)) {

			mainLog.println("\nAll states are in a BSCC (so no reachability probabilities computed)");

			// there's more efficient ways to do this if we just create the
			// solution bdd directly
			// but we actually build the prob vector so it can be printed out if
			// necessary
			tmp = JDD.Constant(0);
			for (i = 0; i < n; i++) {
				bscc = vectBSCCs.elementAt(i);
				JDD.Ref(bscc);
				tmp = JDD.Apply(JDD.PLUS, tmp, JDD.Apply(JDD.TIMES, JDD.Constant(probBSCCs[i]), bscc));
			}
			totalProbs = new StateValuesMTBDD(tmp, model);
		}
		// otherwise we have to do more work...
		else {

			// initialise total probabilities vector
			switch (engine) {
			case Prism.MTBDD:
				totalProbs = new StateValuesMTBDD(JDD.Constant(0), model);
				break;
			case Prism.SPARSE:
				totalProbs = new StateValuesDV(new DoubleVector((int) model.getNumStates()), model);
				break;
			case Prism.HYBRID:
				totalProbs = new StateValuesDV(new DoubleVector((int) model.getNumStates()), model);
				break;
			}

			// compute probabilities of reaching each bscc...
			for (i = 0; i < n; i++) {

				// skip bsccs with zero probability
				if (probBSCCs[i] == 0.0)
					continue;

				mainLog.println("\nComputing probabilities of reaching BSCC " + (i + 1));

				// get bscc
				bscc = vectBSCCs.elementAt(i);

				// compute probabilities
				try {
					probs = computeUntilProbs(trans, trans01, notInBSCCs, bscc);
				} catch (PrismException e) {
					JDD.Deref(b);
					for (i = 0; i < n; i++) {
						JDD.Deref(vectBSCCs.elementAt(i));
					}
					JDD.Deref(notInBSCCs);
					totalProbs.clear();
					throw e;
				}

				// print out probabilities
				if (verbose) {
					mainLog.print("\nBSCC " + (i + 1) + " reachability probabilities: \n");
					probs.print(mainLog);
				}

				// times by bscc prob, add to total
				probs.timesConstant(probBSCCs[i]);
				totalProbs.add(probs);

				// free vector
				probs.clear();
			}
		}

		// print out probabilities
		if (verbose) {
			mainLog.print("\nS operator probabilities: \n");
			totalProbs.print(mainLog);
		}

		// derefs
		JDD.Deref(b);
		for (i = 0; i < n; i++) {
			JDD.Deref(vectBSCCs.elementAt(i));
		}
		JDD.Deref(notInBSCCs);

		// For =? properties, just return values
		if (pb == null) {
			return totalProbs;
		}
		// Otherwise, compare against bound to get set of satisfying states
		else {
			sol = totalProbs.getBDDFromInterval(relOp, p);
			// remove unreachable states from solution
			JDD.Ref(reach);
			sol = JDD.And(sol, reach);
			// free vector
			totalProbs.clear();
			return new StateValuesMTBDD(sol, model);
		}
	}

	// Contents of a P operator

	protected StateValues checkProbPathFormula(Expression expr, boolean qual) throws PrismException
	{
		// Test whether this is a simple path formula (i.e. PCTL)
		// and then pass control to appropriate method. 
		if (expr.isSimplePathFormula()) {
			return checkProbPathFormulaSimple(expr, qual);
		} else {
			return checkProbPathFormulaLTL(expr, qual);
		}
	}

	protected StateValues checkProbPathFormulaSimple(Expression expr, boolean qual) throws PrismException
	{
		StateValues probs = null;

		// Negation/parentheses
		if (expr instanceof ExpressionUnaryOp) {
			ExpressionUnaryOp exprUnary = (ExpressionUnaryOp) expr;
			// Parentheses
			if (exprUnary.getOperator() == ExpressionUnaryOp.PARENTH) {
				// Recurse
				probs = checkProbPathFormulaSimple(exprUnary.getOperand(), qual);
			}
			// Negation
			else if (exprUnary.getOperator() == ExpressionUnaryOp.NOT) {
				// Compute, then subtract from 1 
				probs = checkProbPathFormulaSimple(exprUnary.getOperand(), qual);
				probs.subtractFromOne();
			}
		}
		// Temporal operators
		else if (expr instanceof ExpressionTemporal) {
			ExpressionTemporal exprTemp = (ExpressionTemporal) expr;
			// Next
			if (exprTemp.getOperator() == ExpressionTemporal.P_X) {
				probs = checkProbNext(exprTemp);
			}
			// Until
			else if (exprTemp.getOperator() == ExpressionTemporal.P_U) {
				if (exprTemp.hasBounds()) {
					probs = checkProbBoundedUntil(exprTemp);
				} else {
					probs = checkProbUntil(exprTemp, qual);
				}
			}
			// Anything else - convert to until and recurse
			else {
				probs = checkProbPathFormulaSimple(exprTemp.convertToUntilForm(), qual);
			}
		}

		if (probs == null)
			throw new PrismException("Unrecognised path operator in P operator");

		return probs;
	}

	// LTL-like path formula for P operator

	protected StateValues checkProbPathFormulaLTL(Expression expr, boolean qual) throws PrismException
	{
		LTLModelChecker mcLtl;
		StateValues probsProduct = null, probs = null;
		Expression ltl;
		Vector<JDDNode> labelDDs;
		DRA dra;
		ProbModel modelProduct;
		ProbModelChecker mcProduct;
		JDDNode startMask;
		JDDVars draDDRowVars, draDDColVars;
		int i;
		long l;

		// Can't do LTL with time-bounded variants of the temporal operators
		try {
			expr.accept(new ASTTraverse()
			{
				public void visitPre(ExpressionTemporal e) throws PrismLangException
				{
					if (e.getLowerBound() != null)
						throw new PrismLangException(e.getOperatorSymbol());
					if (e.getUpperBound() != null)
						throw new PrismLangException(e.getOperatorSymbol());
				}
			});
		} catch (PrismLangException e) {
			String s = "Temporal operators (like " + e.getMessage() + ")";
			s += " cannot have time bounds for LTL properties";
			throw new PrismException(s);
		}

		// For LTL model checking routines
		mcLtl = new LTLModelChecker(prism);

		// Model check maximal state formulas
		labelDDs = new Vector<JDDNode>();
		ltl = mcLtl.checkMaximalStateFormulas(this, model, expr.deepCopy(), labelDDs);

		// Convert LTL formula to deterministic Rabin automaton (DRA)
		mainLog.println("\nBuilding deterministic Rabin automaton (for " + ltl + ")...");
		l = System.currentTimeMillis();
		dra = LTL2Rabin.ltl2rabin(ltl.convertForJltl2ba());
		mainLog.println("\nDRA has " + dra.size() + " states, " + dra.acceptance().size() + " pairs.");
		// dra.print(System.out);
		l = System.currentTimeMillis() - l;
		mainLog.println("\nTime for Rabin translation: " + l / 1000.0 + " seconds.");

		// Build product of Markov chain and automaton
		// (note: might be a CTMC - StochModelChecker extends this class)
		mainLog.println("\nConstructing MC-DRA product...");
		draDDRowVars = new JDDVars();
		draDDColVars = new JDDVars();
		modelProduct = mcLtl.constructProductMC(dra, model, labelDDs, draDDRowVars, draDDColVars);
		mainLog.println();
		modelProduct.printTransInfo(mainLog, prism.getExtraDDInfo());
		// prism.exportStatesToFile(modelProduct, Prism.EXPORT_PLAIN, null);
		// prism.exportTransToFile(modelProduct, true, Prism.EXPORT_PLAIN, null);
		
		// Find accepting maximum end BSCC
		mainLog.println("\nFinding accepting BSCCs...");
		JDDNode acc = mcLtl.findAcceptingBSCCs(dra, draDDRowVars, draDDColVars, modelProduct);

		// Compute reachability probabilities
		mainLog.println("\nComputing reachability probabilities...");
		mcProduct = createNewModelChecker(prism, modelProduct, null);
		probsProduct = mcProduct.checkProbUntil(modelProduct.getReach(), acc, qual);
		
		// Convert probability vector to original model
		// First, filter over DRA start states
		startMask = mcLtl.buildStartMask(dra, labelDDs, draDDRowVars);
		JDD.Ref(model.getReach());
		startMask = JDD.And(model.getReach(), startMask);
		probsProduct.filter(startMask);
		// Then sum over DD vars for the DRA state
		probs = probsProduct.sumOverDDVars(draDDRowVars, model);

		// Deref, clean up
		probsProduct.clear();
		modelProduct.clear();
		for (i = 0; i < labelDDs.size(); i++) {
			JDD.Deref(labelDDs.get(i));
		}
		JDD.Deref(acc);
		JDD.Deref(startMask);
		draDDRowVars.derefAll();
		draDDColVars.derefAll();

		return probs;
	}

	// next

	protected StateValues checkProbNext(ExpressionTemporal expr) throws PrismException
	{
		JDDNode b;
		StateValues probs = null;

		// model check operand first
		b = checkExpressionDD(expr.getOperand2());

		// print out some info about num states
		// mainLog.print("\nb = " + JDD.GetNumMintermsString(b,
		// allDDRowVars.n()) + " states\n");

		// compute probabilities
		probs = computeNextProbs(trans, b);

		// derefs
		JDD.Deref(b);

		return probs;
	}

	// bounded until

	protected StateValues checkProbBoundedUntil(ExpressionTemporal expr) throws PrismException
	{
		int time;
		JDDNode b1, b2;
		StateValues probs = null;

		// get info from bounded until
		time = expr.getUpperBound().evaluateInt(constantValues);
		if (expr.upperBoundIsStrict())
			time--;
		if (time < 0) {
			String bound = expr.upperBoundIsStrict() ? "<" + (time + 1) : "<=" + time;
			throw new PrismException("Invalid bound " + bound + " in bounded until formula");
		}

		// model check operands first
		b1 = checkExpressionDD(expr.getOperand1());
		try {
			b2 = checkExpressionDD(expr.getOperand2());
		} catch (PrismException e) {
			JDD.Deref(b1);
			throw e;
		}

		// print out some info about num states
		// mainLog.print("\nb1 = " + JDD.GetNumMintermsString(b1,
		// allDDRowVars.n()));
		// mainLog.print(" states, b2 = " + JDD.GetNumMintermsString(b2,
		// allDDRowVars.n()) + " states\n");

		// compute probabilities

		// a trivial case: "U<=0"
		if (time == 0) {
			// prob is 1 in b2 states, 0 otherwise
			JDD.Ref(b2);
			probs = new StateValuesMTBDD(b2, model);
		} else {
			try {
				probs = computeBoundedUntilProbs(trans, trans01, b1, b2, time);
			} catch (PrismException e) {
				JDD.Deref(b1);
				JDD.Deref(b2);
				throw e;
			}
		}

		// derefs
		JDD.Deref(b1);
		JDD.Deref(b2);

		return probs;
	}

	// until (unbounded)

	// this method is split into two steps so that the LTL model checker can use the second part directly

	protected StateValues checkProbUntil(ExpressionTemporal expr, boolean qual) throws PrismException
	{
		JDDNode b1, b2;
		StateValues probs = null;

		// model check operands first
		b1 = checkExpressionDD(expr.getOperand1());
		try {
			b2 = checkExpressionDD(expr.getOperand2());
		} catch (PrismException e) {
			JDD.Deref(b1);
			throw e;
		}

		// print out some info about num states
		// mainLog.print("\nb1 = " + JDD.GetNumMintermsString(b1,
		// allDDRowVars.n()));
		// mainLog.print(" states, b2 = " + JDD.GetNumMintermsString(b2,
		// allDDRowVars.n()) + " states\n");

		try {
			probs = checkProbUntil(b1, b2, qual);
		} catch (PrismException e) {
			JDD.Deref(b1);
			JDD.Deref(b2);
			throw e;
		}

		// derefs
		JDD.Deref(b1);
		JDD.Deref(b2);

		return probs;
	}

	// until (unbounded): b1/b2 are bdds for until operands

	protected StateValues checkProbUntil(JDDNode b1, JDDNode b2, boolean qual) throws PrismException
	{
		StateValues probs = null;

		// compute probabilities

		// if requested (i.e. when prob bound is 0 or 1 and precomputation algorithms are enabled),
		// compute probabilities qualitatively
		if (qual) {
			mainLog.print("\nProbability bound in formula is 0/1 so not computing exact probabilities...\n");
			probs = computeUntilProbsQual(trans01, b1, b2);
		}
		// otherwise actually compute probabilities
		else {
			probs = computeUntilProbs(trans, trans01, b1, b2);
		}

		return probs;
	}

	// cumulative reward

	protected StateValues checkRewardCumul(ExpressionTemporal expr, JDDNode stateRewards, JDDNode transRewards)
			throws PrismException
	{
		int time; // time
		StateValues rewards = null;

		// get info from inst reward
		time = expr.getUpperBound().evaluateInt(constantValues);
		if (time < 0) {
			throw new PrismException("Invalid time bound " + time + " in cumulative reward formula");
		}

		// compute rewards

		// a trivial case: "<=0"
		if (time == 0) {
			rewards = new StateValuesMTBDD(JDD.Constant(0), model);
		} else {
			// compute rewards
			try {
				rewards = computeCumulRewards(trans, trans01, stateRewards, transRewards, time);
			} catch (PrismException e) {
				throw e;
			}
		}

		return rewards;
	}

	// inst reward

	protected StateValues checkRewardInst(ExpressionTemporal expr, JDDNode stateRewards, JDDNode transRewards)
			throws PrismException
	{
		int time; // time
		StateValues rewards = null;

		// get info from inst reward
		time = expr.getUpperBound().evaluateInt(constantValues);
		if (time < 0) {
			throw new PrismException("Invalid bound " + time + " in instantaneous reward property");
		}

		// compute rewards
		rewards = computeInstRewards(trans, stateRewards, time);

		return rewards;
	}

	// reach reward

	protected StateValues checkRewardReach(ExpressionTemporal expr, JDDNode stateRewards, JDDNode transRewards)
			throws PrismException
	{
		JDDNode b;
		StateValues rewards = null;

		// model check operand first
		b = checkExpressionDD(expr.getOperand2());

		// print out some info about num states
		// mainLog.print("\nb = " + JDD.GetNumMintermsString(b,
		// allDDRowVars.n()) + " states\n");

		// compute rewards
		try {
			rewards = computeReachRewards(trans, trans01, stateRewards, transRewards, b);
		} catch (PrismException e) {
			JDD.Deref(b);
			throw e;
		}

		// derefs
		JDD.Deref(b);

		return rewards;
	}

	// steady state reward

	protected StateValues checkRewardSS(ExpressionTemporal expr, JDDNode stateRewards, JDDNode transRewards)
			throws PrismException
	{
		// bscc stuff
		Vector<JDDNode> vectBSCCs;
		JDDNode notInBSCCs;
		// mtbdd stuff
		JDDNode newStateRewards, bscc, tmp;
		// other stuff
		StateValues probs = null, rewards = null;
		int i, n;
		double d, rewBSCCs[];

		// compute rewards corresponding to each state
		JDD.Ref(trans);
		JDD.Ref(transRewards);
		newStateRewards = JDD.SumAbstract(JDD.Apply(JDD.TIMES, trans, transRewards), allDDColVars);
		JDD.Ref(stateRewards);
		newStateRewards = JDD.Apply(JDD.PLUS, newStateRewards, stateRewards);

		// compute bottom strongly connected components (bsccs)
		if (bsccComp) {
			SCCComputer sccComputer = prism.getSCCComputer(model);
			sccComputer.computeBSCCs();
			vectBSCCs = sccComputer.getVectBSCCs();
			notInBSCCs = sccComputer.getNotInBSCCs();
			n = vectBSCCs.size();
		}
		// unless we've been told to skip it
		else {
			mainLog.println("\nSkipping BSCC computation...");
			vectBSCCs = new Vector<JDDNode>();
			JDD.Ref(reach);
			vectBSCCs.add(reach);
			notInBSCCs = JDD.Constant(0);
			n = 1;
		}

		// compute steady state for each bscc...
		rewBSCCs = new double[n];
		for (i = 0; i < n; i++) {

			mainLog.println("\nComputing steady state probabilities for BSCC " + (i + 1));

			// get bscc
			bscc = vectBSCCs.elementAt(i);

			// compute steady state probabilities
			try {
				probs = computeSteadyStateProbs(trans, bscc);
			} catch (PrismException e) {
				JDD.Deref(newStateRewards);
				for (i = 0; i < n; i++) {
					JDD.Deref(vectBSCCs.elementAt(i));
				}
				JDD.Deref(notInBSCCs);
				throw e;
			}

			// print out probabilities
			if (verbose) {
				mainLog.print("\nBSCC " + (i + 1) + " steady-state probabilities: \n");
				probs.print(mainLog);
			}

			// do weighted sum of probabilities and rewards
			JDD.Ref(bscc);
			JDD.Ref(newStateRewards);
			tmp = JDD.Apply(JDD.TIMES, bscc, newStateRewards);
			d = probs.sumOverMTBDD(tmp);
			rewBSCCs[i] = d;
			mainLog.print("\nBSCC " + (i + 1) + " Reward: " + d + "\n");
			JDD.Deref(tmp);

			// free vector
			probs.clear();
		}

		// if every state is in a bscc, it's much easier...
		if (notInBSCCs.equals(JDD.ZERO)) {

			mainLog.println("\nAll states are in a BSCC (so no reachability probabilities computed)");

			// build the reward vector
			tmp = JDD.Constant(0);
			for (i = 0; i < n; i++) {
				bscc = vectBSCCs.elementAt(i);
				JDD.Ref(bscc);
				tmp = JDD.Apply(JDD.PLUS, tmp, JDD.Apply(JDD.TIMES, JDD.Constant(rewBSCCs[i]), bscc));
			}
			rewards = new StateValuesMTBDD(tmp, model);
		}
		// otherwise we have to do more work...
		else {

			// initialise rewards vector
			switch (engine) {
			case Prism.MTBDD:
				rewards = new StateValuesMTBDD(JDD.Constant(0), model);
				break;
			case Prism.SPARSE:
				rewards = new StateValuesDV(new DoubleVector((int) model.getNumStates()), model);
				break;
			case Prism.HYBRID:
				rewards = new StateValuesDV(new DoubleVector((int) model.getNumStates()), model);
				break;
			}

			// compute probabilities of reaching each bscc...
			for (i = 0; i < n; i++) {

				// skip bsccs with zero reward
				if (rewBSCCs[i] == 0.0)
					continue;

				mainLog.println("\nComputing probabilities of reaching BSCC " + (i + 1));

				// get bscc
				bscc = vectBSCCs.elementAt(i);

				// compute probabilities
				probs = computeUntilProbs(trans, trans01, notInBSCCs, bscc);

				// print out probabilities
				if (verbose) {
					mainLog.print("\nBSCC " + (i + 1) + " reachability probabilities: \n");
					probs.print(mainLog);
				}

				// times by bscc reward, add to total
				probs.timesConstant(rewBSCCs[i]);
				rewards.add(probs);

				// free vector
				probs.clear();
			}
		}

		// derefs
		JDD.Deref(newStateRewards);
		for (i = 0; i < n; i++) {
			JDD.Deref(vectBSCCs.elementAt(i));
		}
		JDD.Deref(notInBSCCs);

		return rewards;
	}

	// -----------------------------------------------------------------------------------
	// do steady state computation
	// -----------------------------------------------------------------------------------

	// steady state computation (from initial states)

	public StateValues doSteadyState() throws PrismException
	{
		// bscc stuff
		Vector<JDDNode> vectBSCCs;
		JDDNode notInBSCCs;
		// mtbdd stuff
		JDDNode start, bscc, tmp;
		// other stuff
		StateValues probs = null, solnProbs = null;
		double d, probBSCCs[];
		int i, n, whichBSCC, bsccCount;

		// compute bottom strongly connected components (bsccs)
		if (bsccComp) {
			SCCComputer sccComputer = prism.getSCCComputer(model);
			sccComputer.computeBSCCs();
			vectBSCCs = sccComputer.getVectBSCCs();
			notInBSCCs = sccComputer.getNotInBSCCs();
			n = vectBSCCs.size();
		}
		// unless we've been told to skip it
		else {
			mainLog.println("\nSkipping BSCC computation...");
			vectBSCCs = new Vector<JDDNode>();
			JDD.Ref(reach);
			vectBSCCs.add(reach);
			notInBSCCs = JDD.Constant(0);
			n = 1;
		}

		// get initial states of model
		start = model.getStart();

		// see how many bsccs contain initial states and, if just one, which one
		whichBSCC = -1;
		bsccCount = 0;
		for (i = 0; i < n; i++) {
			bscc = vectBSCCs.elementAt(i);
			JDD.Ref(bscc);
			JDD.Ref(start);
			tmp = JDD.And(bscc, start);
			if (!tmp.equals(JDD.ZERO)) {
				bsccCount++;
				if (bsccCount == 1)
					whichBSCC = i;
			}
			JDD.Deref(tmp);
		}

		// if all initial states are in a single bscc, it's easy...
		JDD.Ref(notInBSCCs);
		JDD.Ref(start);
		tmp = JDD.And(notInBSCCs, start);
		if (tmp.equals(JDD.ZERO) && bsccCount == 1) {

			JDD.Deref(tmp);

			mainLog.println("\nInitial states all in one BSCC (so no reachability probabilities computed)");

			// get bscc
			bscc = vectBSCCs.elementAt(whichBSCC);

			// compute steady-state probabilities for the bscc
			try {
				solnProbs = computeSteadyStateProbs(trans, bscc);
			} catch (PrismException e) {
				for (i = 0; i < n; i++) {
					JDD.Deref(vectBSCCs.elementAt(i));
				}
				JDD.Deref(notInBSCCs);
				throw e;
			}
		}

		// otherwise have to consider all the bsccs
		else {

			JDD.Deref(tmp);

			// initialise total probabilities vector
			switch (engine) {
			case Prism.MTBDD:
				solnProbs = new StateValuesMTBDD(JDD.Constant(0), model);
				break;
			case Prism.SPARSE:
				solnProbs = new StateValuesDV(new DoubleVector((int) model.getNumStates()), model);
				break;
			case Prism.HYBRID:
				solnProbs = new StateValuesDV(new DoubleVector((int) model.getNumStates()), model);
				break;
			}

			// compute prob of reaching each bscc from initial state
			probBSCCs = new double[n];
			for (i = 0; i < n; i++) {

				mainLog.println("\nComputing probability of reaching BSCC " + (i + 1));

				// get bscc
				bscc = vectBSCCs.elementAt(i);

				// compute probabilities
				try {
					probs = computeUntilProbs(trans, trans01, notInBSCCs, bscc);
				} catch (PrismException e) {
					for (i = 0; i < n; i++) {
						JDD.Deref(vectBSCCs.elementAt(i));
					}
					JDD.Deref(notInBSCCs);
					solnProbs.clear();
					throw e;
				}

				// sum probabilities over bdd for initial state
				// and then divide by number of start states
				// (we assume an equiprobable initial probability distribution
				// over all initial states)
				d = probs.sumOverBDD(start);
				d /= model.getNumStartStates();
				probBSCCs[i] = d;
				mainLog.print("\nBSCC " + (i + 1) + " Probability: " + d + "\n");

				// free vector
				probs.clear();
			}

			// compute steady-state for each bscc
			for (i = 0; i < n; i++) {

				mainLog.println("\nComputing steady-state probabilities for BSCC " + (i + 1));

				// get bscc
				bscc = vectBSCCs.elementAt(i);

				// compute steady-state probabilities for the bscc
				try {
					probs = computeSteadyStateProbs(trans, bscc);
				} catch (PrismException e) {
					for (i = 0; i < n; i++) {
						JDD.Deref(vectBSCCs.elementAt(i));
					}
					JDD.Deref(notInBSCCs);
					solnProbs.clear();
					throw e;
				}

				// print out probabilities
				if (verbose) {
					mainLog.print("\nBSCC " + (i + 1) + " Steady-State Probabilities: \n");
					probs.print(mainLog);
				}

				// times by bscc reach prob, add to total
				probs.timesConstant(probBSCCs[i]);
				solnProbs.add(probs);

				// free vector
				probs.clear();
			}
		}

		// derefs
		for (i = 0; i < n; i++) {
			JDD.Deref(vectBSCCs.elementAt(i));
		}
		JDD.Deref(notInBSCCs);

		return solnProbs;
	}

	// -----------------------------------------------------------------------------------
	// do transient computation
	// -----------------------------------------------------------------------------------

	/**
	 * Compute transient probability distribution (forwards).
	 * Start from initial state (or uniform distribution over multiple initial states).
	 */
	public StateValues doTransient(int time) throws PrismException
	{
		return doTransient(time, (StateValues) null);
	}
	
	/**
	 * Compute transient probability distribution (forwards).
	 * Optionally, use the passed in file initDistFile to give the initial probability distribution (time 0).
	 * If null, start from initial state (or uniform distribution over multiple initial states).
	 */
	public StateValues doTransient(int time, File initDistFile) throws PrismException
	{
		StateValues initDist = null;

		if (initDistFile != null) {
			mainLog.println("\nImporting initial probability distribution from file \"" + initDistFile + "\"...");
			// Build an empty vector of the appropriate type 
			if (engine == Prism.MTBDD) {
				initDist = new StateValuesMTBDD(JDD.Constant(0), model);
			} else {
				initDist = new StateValuesDV(new DoubleVector((int) model.getNumStates()), model);
			}
			// Populate vector from file
			initDist.readFromFile(initDistFile);
		}
		
		return doTransient(time, initDist);
	}

	/**
	 * Compute transient probability distribution (forwards).
	 * Optionally, use the passed in vector initDist as the initial probability distribution (time 0).
	 * If null, start from initial state (or uniform distribution over multiple initial states).
	 * For reasons of efficiency, when a vector is passed in, it will be trampled over and
	 * then deleted afterwards, so if you wanted it, take a copy. 
	 */
	public StateValues doTransient(int time, StateValues initDist) throws PrismException
	{
		// mtbdd stuff
		JDDNode start, init;
		// other stuff
		StateValues initDistNew = null, probs = null;

		// build initial distribution (if not specified)
		if (initDist == null) {
			// first construct as MTBDD
			// get initial states of model
			start = model.getStart();
			// compute initial probability distribution (equiprobable over all start states)
			JDD.Ref(start);
			init = JDD.Apply(JDD.DIVIDE, start, JDD.Constant(JDD.GetNumMinterms(start, allDDRowVars.n())));
			// if using MTBDD engine, distribution needs to be an MTBDD
			if (engine == Prism.MTBDD) {
				initDistNew = new StateValuesMTBDD(init, model);
			}
			// for sparse/hybrid engines, distribution needs to be a double vector
			else {
				initDistNew = new StateValuesDV(init, model);
				JDD.Deref(init);
			}
		} else {
			initDistNew = initDist;
		}
		
		// compute transient probabilities
		probs = computeTransientProbs(trans, initDistNew, time);

		return probs;
	}

	// -----------------------------------------------------------------------------------
	// probability computation methods
	// -----------------------------------------------------------------------------------

	// compute probabilities for next

	protected StateValues computeNextProbs(JDDNode tr, JDDNode b)
	{
		JDDNode tmp;
		StateValues probs = null;

		// matrix multiply: trans * b
		JDD.Ref(b);
		tmp = JDD.PermuteVariables(b, allDDRowVars, allDDColVars);
		JDD.Ref(tr);
		tmp = JDD.MatrixMultiply(tr, tmp, allDDColVars, JDD.BOULDER);
		probs = new StateValuesMTBDD(tmp, model);

		return probs;
	}

	// compute probabilities for bounded until

	protected StateValues computeBoundedUntilProbs(JDDNode tr, JDDNode tr01, JDDNode b1, JDDNode b2, int time)
			throws PrismException
	{
		JDDNode yes, no, maybe;
		JDDNode probsMTBDD;
		DoubleVector probsDV;
		StateValues probs = null;

		// compute yes/no/maybe states
		if (b2.equals(JDD.ZERO)) {
			yes = JDD.Constant(0);
			JDD.Ref(reach);
			no = reach;
			maybe = JDD.Constant(0);
		} else if (b1.equals(JDD.ZERO)) {
			JDD.Ref(b2);
			yes = b2;
			JDD.Ref(reach);
			JDD.Ref(b2);
			no = JDD.And(reach, JDD.Not(b2));
			maybe = JDD.Constant(0);
		} else {
			// yes
			JDD.Ref(b2);
			yes = b2;
			// no
			if (yes.equals(reach)) {
				no = JDD.Constant(0);
			} else if (precomp && prob0) {
				no = PrismMTBDD.Prob0(tr01, reach, allDDRowVars, allDDColVars, b1, yes);
			} else {
				JDD.Ref(reach);
				JDD.Ref(b1);
				JDD.Ref(b2);
				no = JDD.And(reach, JDD.Not(JDD.Or(b1, b2)));
			}
			// maybe
			JDD.Ref(reach);
			JDD.Ref(yes);
			JDD.Ref(no);
			maybe = JDD.And(reach, JDD.Not(JDD.Or(yes, no)));
		}

		// print out yes/no/maybe
		mainLog.print("\nyes = " + JDD.GetNumMintermsString(yes, allDDRowVars.n()));
		mainLog.print(", no = " + JDD.GetNumMintermsString(no, allDDRowVars.n()));
		mainLog.print(", maybe = " + JDD.GetNumMintermsString(maybe, allDDRowVars.n()) + "\n");

		// if maybe is empty, we have the probabilities already
		if (maybe.equals(JDD.ZERO)) {
			JDD.Ref(yes);
			probs = new StateValuesMTBDD(yes, model);
		}
		// otherwise explicitly compute the remaining probabilities
		else {
			// compute probabilities
			try {
				switch (engine) {
				case Prism.MTBDD:
					probsMTBDD = PrismMTBDD.ProbBoundedUntil(tr, odd, allDDRowVars, allDDColVars, yes, maybe, time);
					probs = new StateValuesMTBDD(probsMTBDD, model);
					break;
				case Prism.SPARSE:
					probsDV = PrismSparse.ProbBoundedUntil(tr, odd, allDDRowVars, allDDColVars, yes, maybe, time);
					probs = new StateValuesDV(probsDV, model);
					break;
				case Prism.HYBRID:
					probsDV = PrismHybrid.ProbBoundedUntil(tr, odd, allDDRowVars, allDDColVars, yes, maybe, time);
					probs = new StateValuesDV(probsDV, model);
					break;
				default:
					throw new PrismException("Unknown engine");
				}
			} catch (PrismException e) {
				JDD.Deref(yes);
				JDD.Deref(no);
				JDD.Deref(maybe);
				throw e;
			}
		}

		// derefs
		JDD.Deref(yes);
		JDD.Deref(no);
		JDD.Deref(maybe);

		return probs;
	}

	// compute probabilities for until (for qualitative properties)

	protected StateValues computeUntilProbsQual(JDDNode tr01, JDDNode b1, JDDNode b2)
	{
		JDDNode yes, no, maybe;
		StateValues probs = null;

		// note: we know precomputation is enabled else this function wouldn't
		// have been called

		// compute yes/no/maybe states
		if (b2.equals(JDD.ZERO)) {
			yes = JDD.Constant(0);
			JDD.Ref(reach);
			no = reach;
			maybe = JDD.Constant(0);
		} else if (b1.equals(JDD.ZERO)) {
			JDD.Ref(b2);
			yes = b2;
			JDD.Ref(reach);
			JDD.Ref(b2);
			no = JDD.And(reach, JDD.Not(b2));
			maybe = JDD.Constant(0);
		} else {
			// no/yes
			no = PrismMTBDD.Prob0(tr01, reach, allDDRowVars, allDDColVars, b1, b2);
			yes = PrismMTBDD.Prob1(tr01, reach, allDDRowVars, allDDColVars, b1, b2, no);
			// maybe
			JDD.Ref(reach);
			JDD.Ref(yes);
			JDD.Ref(no);
			maybe = JDD.And(reach, JDD.Not(JDD.Or(yes, no)));
		}

		// print out yes/no/maybe
		mainLog.print("\nyes = " + JDD.GetNumMintermsString(yes, allDDRowVars.n()));
		mainLog.print(", no = " + JDD.GetNumMintermsString(no, allDDRowVars.n()));
		mainLog.print(", maybe = " + JDD.GetNumMintermsString(maybe, allDDRowVars.n()) + "\n");

		// if maybe is empty, we have the probabilities already
		if (maybe.equals(JDD.ZERO)) {
			JDD.Ref(yes);
			probs = new StateValuesMTBDD(yes, model);
		}
		// otherwise we set the probabilities for maybe states to be 0.5
		// (actual probabilities for these states are unknown but definitely >0
		// and <1)
		// (this is safe because the results of this function will only be used
		// to compare against 0/1 bounds)
		// (this is not entirely elegant but is simpler and less error prone
		// than
		// trying to work out whether to use 0/1 for all case of future/global, etc.)
		else {
			JDD.Ref(yes);
			JDD.Ref(maybe);
			probs = new StateValuesMTBDD(JDD.Apply(JDD.PLUS, yes, JDD.Apply(JDD.TIMES, maybe, JDD.Constant(0.5))), model);
		}

		// derefs
		JDD.Deref(yes);
		JDD.Deref(no);
		JDD.Deref(maybe);

		return probs;
	}

	// compute probabilities for until (general case)

	protected StateValues computeUntilProbs(JDDNode tr, JDDNode tr01, JDDNode b1, JDDNode b2) throws PrismException
	{
		JDDNode yes, no, maybe;
		JDDNode probsMTBDD;
		DoubleVector probsDV;
		StateValues probs = null;

		// If required, export info about target states 
		if (prism.getExportTarget()) {
			JDDNode labels[] = { model.getStart(), b2 };
			String labelNames[] = { "init", "target" };
			try {
				mainLog.println("\nExporting target states info to file \"" + prism.getExportTargetFilename() + "\"...");
				PrismMTBDD.ExportLabels(labels, labelNames, "l", model.getAllDDRowVars(), model.getODD(), Prism.EXPORT_PLAIN, prism.getExportTargetFilename());
			} catch (FileNotFoundException e) {
				mainLog.printWarning("Could not export target to file \"" + prism.getExportTargetFilename() + "\"");
			}
		}
		
		// compute yes/no/maybe states
		if (b2.equals(JDD.ZERO)) {
			yes = JDD.Constant(0);
			JDD.Ref(reach);
			no = reach;
			maybe = JDD.Constant(0);
		} else if (b1.equals(JDD.ZERO)) {
			JDD.Ref(b2);
			yes = b2;
			JDD.Ref(reach);
			JDD.Ref(b2);
			no = JDD.And(reach, JDD.Not(b2));
			maybe = JDD.Constant(0);
		} else {
			// no/yes
			if (precomp && (prob0 || prob1)) {
				no = PrismMTBDD.Prob0(tr01, reach, allDDRowVars, allDDColVars, b1, b2);
			} else {
				JDD.Ref(reach);
				JDD.Ref(b1);
				JDD.Ref(b2);
				no = JDD.And(reach, JDD.Not(JDD.Or(b1, b2)));
			}
			if (precomp && prob1) {
				yes = PrismMTBDD.Prob1(tr01, reach, allDDRowVars, allDDColVars, b1, b2, no);
			} else {
				JDD.Ref(b2);
				yes = b2;
			}
			// maybe
			JDD.Ref(reach);
			JDD.Ref(yes);
			JDD.Ref(no);
			maybe = JDD.And(reach, JDD.Not(JDD.Or(yes, no)));
		}

		// print out yes/no/maybe
		mainLog.print("\nyes = " + JDD.GetNumMintermsString(yes, allDDRowVars.n()));
		mainLog.print(", no = " + JDD.GetNumMintermsString(no, allDDRowVars.n()));
		mainLog.print(", maybe = " + JDD.GetNumMintermsString(maybe, allDDRowVars.n()) + "\n");

		// if maybe is empty, we have the probabilities already
		if (maybe.equals(JDD.ZERO)) {
			// we make sure to return a vector of the appropriate type
			// (doublevector for hybrid/sparse, mtbdd for mtbdd)
			switch (engine) {
			case Prism.MTBDD:
				JDD.Ref(yes);
				probs = new StateValuesMTBDD(yes, model);
				break;
			case Prism.SPARSE:
			case Prism.HYBRID:
				probs = new StateValuesDV(yes, model);
				break;
			}
		}
		// otherwise we compute the actual probabilities
		else {
			// compute probabilities
			mainLog.println("\nComputing remaining probabilities...");

			try {
				switch (engine) {
				case Prism.MTBDD:
					probsMTBDD = PrismMTBDD.ProbUntil(tr, odd, allDDRowVars, allDDColVars, yes, maybe);
					probs = new StateValuesMTBDD(probsMTBDD, model);
					break;
				case Prism.SPARSE:
					probsDV = PrismSparse.ProbUntil(tr, odd, allDDRowVars, allDDColVars, yes, maybe);
					probs = new StateValuesDV(probsDV, model);
					break;
				case Prism.HYBRID:
					probsDV = PrismHybrid.ProbUntil(tr, odd, allDDRowVars, allDDColVars, yes, maybe);
					probs = new StateValuesDV(probsDV, model);
					break;
				default:
					throw new PrismException("Unknown engine");
				}
			} catch (PrismException e) {
				JDD.Deref(yes);
				JDD.Deref(no);
				JDD.Deref(maybe);
				throw e;
			}
		}

		// derefs
		JDD.Deref(yes);
		JDD.Deref(no);
		JDD.Deref(maybe);

		return probs;
	}

	// compute cumulative rewards

	protected StateValues computeCumulRewards(JDDNode tr, JDDNode tr01, JDDNode sr, JDDNode trr, int time)
			throws PrismException
	{
		JDDNode rewardsMTBDD;
		DoubleVector rewardsDV;
		StateValues rewards = null;

		// compute rewards
		try {
			switch (engine) {
			case Prism.MTBDD:
				rewardsMTBDD = PrismMTBDD.ProbCumulReward(tr, sr, trr, odd, allDDRowVars, allDDColVars, time);
				rewards = new StateValuesMTBDD(rewardsMTBDD, model);
				break;
			case Prism.SPARSE:
				rewardsDV = PrismSparse.ProbCumulReward(tr, sr, trr, odd, allDDRowVars, allDDColVars, time);
				rewards = new StateValuesDV(rewardsDV, model);
				break;
			case Prism.HYBRID:
				rewardsDV = PrismHybrid.ProbCumulReward(tr, sr, trr, odd, allDDRowVars, allDDColVars, time);
				rewards = new StateValuesDV(rewardsDV, model);
				break;
			default:
				throw new PrismException("Unknown engine");
			}
		} catch (PrismException e) {
			throw e;
		}

		return rewards;
	}

	// compute rewards for inst reward

	protected StateValues computeInstRewards(JDDNode tr, JDDNode sr, int time) throws PrismException
	{
		JDDNode rewardsMTBDD;
		DoubleVector rewardsDV;
		StateValues rewards = null;

		// a trivial case: "=0"
		if (time == 0) {
			JDD.Ref(sr);
			rewards = new StateValuesMTBDD(sr, model);
		}
		// otherwise we compute the actual rewards
		else {
			// compute the rewards
			try {
				switch (engine) {
				case Prism.MTBDD:
					rewardsMTBDD = PrismMTBDD.ProbInstReward(tr, sr, odd, allDDRowVars, allDDColVars, time);
					rewards = new StateValuesMTBDD(rewardsMTBDD, model);
					break;
				case Prism.SPARSE:
					rewardsDV = PrismSparse.ProbInstReward(tr, sr, odd, allDDRowVars, allDDColVars, time);
					rewards = new StateValuesDV(rewardsDV, model);
					break;
				case Prism.HYBRID:
					rewardsDV = PrismHybrid.ProbInstReward(tr, sr, odd, allDDRowVars, allDDColVars, time);
					rewards = new StateValuesDV(rewardsDV, model);
					break;
				default:
					throw new PrismException("Unknown engine");
				}
			} catch (PrismException e) {
				throw e;
			}
		}

		return rewards;
	}

	// compute rewards for reach reward

	protected StateValues computeReachRewards(JDDNode tr, JDDNode tr01, JDDNode sr, JDDNode trr, JDDNode b)
			throws PrismException
	{
		JDDNode inf, maybe;
		JDDNode rewardsMTBDD;
		DoubleVector rewardsDV;
		StateValues rewards = null;

		// compute states which can't reach goal with probability 1
		if (b.equals(JDD.ZERO)) {
			JDD.Ref(reach);
			inf = reach;
			maybe = JDD.Constant(0);
		} else if (b.equals(reach)) {
			inf = JDD.Constant(0);
			maybe = JDD.Constant(0);
		} else {
			JDDNode no = PrismMTBDD.Prob0(tr01, reach, allDDRowVars, allDDColVars, reach, b);
			JDDNode prob1 = PrismMTBDD.Prob1(tr01, reach, allDDRowVars, allDDColVars, reach, b, no);
			JDD.Deref(no);
			JDD.Ref(reach);
			inf = JDD.And(reach, JDD.Not(prob1));
			JDD.Ref(reach);
			JDD.Ref(inf);
			JDD.Ref(b);
			maybe = JDD.And(reach, JDD.Not(JDD.Or(inf, b)));
		}

		// print out yes/no/maybe
		mainLog.print("\ngoal = " + JDD.GetNumMintermsString(b, allDDRowVars.n()));
		mainLog.print(", inf = " + JDD.GetNumMintermsString(inf, allDDRowVars.n()));
		mainLog.print(", maybe = " + JDD.GetNumMintermsString(maybe, allDDRowVars.n()) + "\n");

		// if maybe is empty, we have the rewards already
		if (maybe.equals(JDD.ZERO)) {
			JDD.Ref(inf);
			rewards = new StateValuesMTBDD(JDD.ITE(inf, JDD.PlusInfinity(), JDD.Constant(0)), model);
		}
		// otherwise we compute the actual rewards
		else {
			// compute the rewards
			mainLog.println("\nComputing remaining rewards...");

			try {
				switch (engine) {
				case Prism.MTBDD:
					rewardsMTBDD = PrismMTBDD.ProbReachReward(tr, sr, trr, odd, allDDRowVars, allDDColVars, b, inf,
							maybe);
					rewards = new StateValuesMTBDD(rewardsMTBDD, model);
					break;
				case Prism.SPARSE:
					rewardsDV = PrismSparse
							.ProbReachReward(tr, sr, trr, odd, allDDRowVars, allDDColVars, b, inf, maybe);
					rewards = new StateValuesDV(rewardsDV, model);
					break;
				case Prism.HYBRID:
					rewardsDV = PrismHybrid
							.ProbReachReward(tr, sr, trr, odd, allDDRowVars, allDDColVars, b, inf, maybe);
					rewards = new StateValuesDV(rewardsDV, model);
					break;
				default:
					throw new PrismException("Unknown engine");
				}
			} catch (PrismException e) {
				JDD.Deref(inf);
				JDD.Deref(maybe);
				throw e;
			}
		}

		// derefs
		JDD.Deref(inf);
		JDD.Deref(maybe);

		return rewards;
	}

	// compute steady-state probabilities

	// tr = the rate matrix for the whole Markov chain
	// states = the subset of reachable states (e.g. bscc) for which
	// steady-state is to be done

	protected StateValues computeSteadyStateProbs(JDDNode tr, JDDNode subset) throws PrismException
	{
		JDDNode trf, init;
		long n;
		JDDNode probsMTBDD;
		DoubleVector probsDV;
		StateValues probs = null;

		// work out number of states in 'subset'
		if (tr.equals(reach)) {
			// avoid a call to GetNumMinterms in this simple (and common) case
			n = model.getNumStates();
		} else {
			n = Math.round(JDD.GetNumMinterms(subset, allDDRowVars.n()));
		}

		// special case - there is only one state in 'subset'
		// (in fact, we need to check for this special case because the general
		// solution work breaks)
		if (n == 1) {
			// answer is trivially one in the single state
			switch (engine) {
			case Prism.MTBDD:
				JDD.Ref(subset);
				return new StateValuesMTBDD(subset, model);
			case Prism.SPARSE:
				return new StateValuesDV(subset, model);
			case Prism.HYBRID:
				return new StateValuesDV(subset, model);
			}
		}

		// filter out unwanted states from transition matrix
		JDD.Ref(tr);
		JDD.Ref(subset);
		trf = JDD.Apply(JDD.TIMES, tr, subset);
		JDD.Ref(subset);
		trf = JDD.Apply(JDD.TIMES, trf, JDD.PermuteVariables(subset, allDDRowVars, allDDColVars));

		// compute initial solution (equiprobable)
		JDD.Ref(subset);
		init = JDD.Apply(JDD.DIVIDE, subset, JDD.Constant(n));

		try {
			switch (engine) {
			case Prism.MTBDD:
				probsMTBDD = PrismMTBDD.StochSteadyState(trf, odd, init, allDDRowVars, allDDColVars);
				probs = new StateValuesMTBDD(probsMTBDD, model);
				break;
			case Prism.SPARSE:
				probsDV = PrismSparse.StochSteadyState(trf, odd, init, allDDRowVars, allDDColVars);
				probs = new StateValuesDV(probsDV, model);
				break;
			case Prism.HYBRID:
				probsDV = PrismHybrid.StochSteadyState(trf, odd, init, allDDRowVars, allDDColVars);
				probs = new StateValuesDV(probsDV, model);
				break;
			default:
				throw new PrismException("Unknown engine");
			}
		} catch (PrismException e) {
			JDD.Deref(trf);
			JDD.Deref(init);
			throw e;
		}

		// derefs
		JDD.Deref(trf);
		JDD.Deref(init);

		return probs;
	}

	/**
	 * Compute transient probability distribution (forwards).
	 * Use the passed in vector initDist as the initial probability distribution (time 0).
	 * The type of this should match the current engine
	 * (i.e. StateValuesMTBDD for MTBDD, StateValuesDV for sparse/hybrid). 
	 * For reasons of efficiency, this vector will be trampled over and
	 * then deleted afterwards, so if you wanted it, take a copy. 
	 */
	protected StateValues computeTransientProbs(JDDNode tr, StateValues initDist, int time) throws PrismException
	{
		JDDNode probsMTBDD;
		DoubleVector probsDV;
		StateValues probs = null;

		// special case: time = 0
		if (time == 0) {
			// we are allowed to keep the init vector, so no need to clone
			return initDist;
		}
		
		// general case
		try {
			switch (engine) {
			case Prism.MTBDD:
				probsMTBDD = PrismMTBDD.ProbTransient(tr, odd, ((StateValuesMTBDD) initDist).getJDDNode(), allDDRowVars, allDDColVars, time);
				probs = new StateValuesMTBDD(probsMTBDD, model);
				break;
			case Prism.SPARSE:
				probsDV = PrismSparse.ProbTransient(tr, odd, ((StateValuesDV) initDist).getDoubleVector(), allDDRowVars, allDDColVars, time);
				probs = new StateValuesDV(probsDV, model);
				break;
			case Prism.HYBRID:
				probsDV = PrismHybrid.ProbTransient(tr, odd, ((StateValuesDV) initDist).getDoubleVector(), allDDRowVars, allDDColVars, time);
				probs = new StateValuesDV(probsDV, model);
				break;
			default:
				throw new PrismException("Unknown engine");
			}
		} catch (PrismException e) {
			throw e;
		}

		return probs;
	}
}

// ------------------------------------------------------------------------------
