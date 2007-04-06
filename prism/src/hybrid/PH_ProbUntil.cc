//==============================================================================
//	
//	Copyright (c) 2002-
//	Authors:
//	* Dave Parker <dxp@cs.bham.uc.uk> (University of Birmingham)
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

// includes
#include "PrismHybrid.h"
#include <math.h>
#include <util.h>
#include <cudd.h>
#include <dd.h>
#include <odd.h>
#include <dv.h>
#include "sparse.h"
#include "hybrid.h"
#include "PrismHybridGlob.h"

//------------------------------------------------------------------------------

JNIEXPORT jint JNICALL Java_hybrid_PrismHybrid_PH_1ProbUntil
(
JNIEnv *env,
jclass cls,
jint t,			// trans matrix
jint od,		// odd
jint rv,		// row vars
jint num_rvars,
jint cv,		// col vars
jint num_cvars,
jint y,			// 'yes' states
jint m			// 'maybe' states
)
{
	// cast function parameters
	DdNode *trans = (DdNode *)t;	// trans matrix
	ODDNode *odd = (ODDNode *)od; 	// reachable states
	DdNode **rvars = (DdNode **)rv; // row vars
	DdNode **cvars = (DdNode **)cv; // col vars
	DdNode *yes = (DdNode *)y;		// 'yes' states
	DdNode *maybe = (DdNode *)m; 	// 'maybe' states
	// mtbdds
	DdNode *reach, *transr, *a, *b, *tmp;
	// vectors
	double *soln;
	
	// get reachable states
	reach = odd->dd;
	
	// filter out rows
	Cudd_Ref(trans);
	Cudd_Ref(maybe);
	a = DD_Apply(ddman, APPLY_TIMES, trans, maybe);
	
	// subtract a from identity (unless we are going to solve with the power method)
	if (lin_eq_method != LIN_EQ_METHOD_POWER) {
		tmp = DD_Identity(ddman, rvars, cvars, num_rvars);
		Cudd_Ref(reach);
		tmp = DD_And(ddman, tmp, reach);
		a = DD_Apply(ddman, APPLY_MINUS, tmp, a);
	}
	
	// build b
	Cudd_Ref(yes);
	b = yes;
	
	// call iterative method
	soln = NULL;
	switch (lin_eq_method) {
		case LIN_EQ_METHOD_POWER:
			soln = (double *)Java_hybrid_PrismHybrid_PH_1Power(env, cls, (jint)odd, (jint)rvars, num_rvars, (jint)cvars, num_cvars, (jint)a, (jint)b, (jint)b, false); break;
		case LIN_EQ_METHOD_JACOBI:
			soln = (double *)Java_hybrid_PrismHybrid_PH_1JOR(env, cls, (jint)odd, (jint)rvars, num_rvars, (jint)cvars, num_cvars, (jint)a, (jint)b, (jint)b, false, false, 1.0); break;
		case LIN_EQ_METHOD_GAUSSSEIDEL:
			soln = (double *)Java_hybrid_PrismHybrid_PH_1SOR(env, cls, (jint)odd, (jint)rvars, num_rvars, (jint)cvars, num_cvars, (jint)a, (jint)b, (jint)b, false, false, 1.0, true); break;
		case LIN_EQ_METHOD_BGAUSSSEIDEL:
			soln = (double *)Java_hybrid_PrismHybrid_PH_1SOR(env, cls, (jint)odd, (jint)rvars, num_rvars, (jint)cvars, num_cvars, (jint)a, (jint)b, (jint)b, false, false, 1.0, false); break;
		case LIN_EQ_METHOD_PGAUSSSEIDEL:
			soln = (double *)Java_hybrid_PrismHybrid_PH_1PSOR(env, cls, (jint)odd, (jint)rvars, num_rvars, (jint)cvars, num_cvars, (jint)a, (jint)b, (jint)b, false, false, 1.0, true); break;
		case LIN_EQ_METHOD_BPGAUSSSEIDEL:
			soln = (double *)Java_hybrid_PrismHybrid_PH_1PSOR(env, cls, (jint)odd, (jint)rvars, num_rvars, (jint)cvars, num_cvars, (jint)a, (jint)b, (jint)b, false, false, 1.0, false); break;
		case LIN_EQ_METHOD_JOR:
			soln = (double *)Java_hybrid_PrismHybrid_PH_1JOR(env, cls, (jint)odd, (jint)rvars, num_rvars, (jint)cvars, num_cvars, (jint)a, (jint)b, (jint)b, false, false, lin_eq_method_param); break;
		case LIN_EQ_METHOD_SOR:
			soln = (double *)Java_hybrid_PrismHybrid_PH_1SOR(env, cls, (jint)odd, (jint)rvars, num_rvars, (jint)cvars, num_cvars, (jint)a, (jint)b, (jint)b, false, false, lin_eq_method_param, true); break;
		case LIN_EQ_METHOD_BSOR:
			soln = (double *)Java_hybrid_PrismHybrid_PH_1SOR(env, cls, (jint)odd, (jint)rvars, num_rvars, (jint)cvars, num_cvars, (jint)a, (jint)b, (jint)b, false, false, lin_eq_method_param, false); break;
		case LIN_EQ_METHOD_PSOR:
			soln = (double *)Java_hybrid_PrismHybrid_PH_1PSOR(env, cls, (jint)odd, (jint)rvars, num_rvars, (jint)cvars, num_cvars, (jint)a, (jint)b, (jint)b, false, false, lin_eq_method_param, true); break;
		case LIN_EQ_METHOD_BPSOR:
			soln = (double *)Java_hybrid_PrismHybrid_PH_1PSOR(env, cls, (jint)odd, (jint)rvars, num_rvars, (jint)cvars, num_cvars, (jint)a, (jint)b, (jint)b, false, false, lin_eq_method_param, false); break;
	}
	
	// free memory
	Cudd_RecursiveDeref(ddman, a);
	Cudd_RecursiveDeref(ddman, b);
	
	return (int)soln;
}

//------------------------------------------------------------------------------
