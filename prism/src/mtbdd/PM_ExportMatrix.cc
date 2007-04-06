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
#include "PrismMTBDD.h"
#include <util.h>
#include <cudd.h>
#include <dd.h>
#include <odd.h>
#include "PrismMTBDDGlob.h"

//------------------------------------------------------------------------------

// local function prototypes
static void export_rec(DdNode *dd, DdNode **rvars, DdNode **cvars, int num_vars, int level, ODDNode *row, ODDNode *col, long r, long c);

// globals
static const char *export_name;

//------------------------------------------------------------------------------

JNIEXPORT jint JNICALL Java_mtbdd_PrismMTBDD_PM_1ExportMatrix
(
JNIEnv *env,
jclass cls,
jint m,			// matrix
jstring na,		// matrix name
jint rv,		// row vars
jint num_rvars,
jint cv,		// col vars
jint num_cvars,
jint od,		// odd
jint et,		// export type
jstring fn		// filename
)
{
	DdNode *matrix = (DdNode *)m;	// matrix
	DdNode **rvars = (DdNode **)rv; // row vars
	DdNode **cvars = (DdNode **)cv; // col vars
	ODDNode *odd = (ODDNode *)od;
	
	// store export info
	if (!store_export_info(et, fn, env)) return -1;
	export_name = na ? env->GetStringUTFChars(na, 0) : "M";
	
	// print file header
	switch (export_type) {
	case EXPORT_PLAIN: export_string("%d %.0f\n", odd->eoff+odd->toff, DD_GetNumMinterms(ddman, matrix, num_rvars+num_cvars)); break;
	case EXPORT_MATLAB: export_string("%s = sparse(%d,%d);\n", export_name, odd->eoff+odd->toff, odd->eoff+odd->toff); break;
	case EXPORT_DOT: export_string("digraph %s {\nsize=\"8,5\"\norientation=land;\nnode [shape = circle];\n", export_name); break;
	}
	
	// print main part of file
	export_rec(matrix, rvars, cvars, num_rvars, 0, odd, odd, 0, 0);
	
	// print file footer
	switch (export_type) {
	case EXPORT_DOT: export_string("}\n"); break;
	}
	
	// close file, etc.
	if (export_file) fclose(export_file);
	env->ReleaseStringUTFChars(na, export_name);
	
	return 0;
}

//------------------------------------------------------------------------------

void export_rec(DdNode *dd, DdNode **rvars, DdNode **cvars, int num_vars, int level, ODDNode *row, ODDNode *col, long r, long c)
{
	DdNode *e, *t, *ee, *et, *te, *tt;
	
	// base case - zero terminal
	if (dd == Cudd_ReadZero(ddman)) return;
	
	// base case - non zero terminal
	if (level == num_vars) {
		switch (export_type) {
		case EXPORT_PLAIN: export_string("%d %d %.12g\n", r, c, Cudd_V(dd)); break;
		case EXPORT_MATLAB: export_string("%s(%d,%d)=%.12g;\n", export_name, r+1, c+1, Cudd_V(dd)); break;
		case EXPORT_DOT: export_string("%d -> %d [ label=\"%.12g\" ];\n", r, c, Cudd_V(dd)); break;
		}
		return;
	}
	
	// recurse
	if (dd->index > cvars[level]->index) {
		ee = et = te = tt = dd;
	}
	else if (dd->index > rvars[level]->index) {
		ee = te = Cudd_E(dd);
		et = tt = Cudd_T(dd);
	}
	else {
		e = Cudd_E(dd);
		if (e->index > cvars[level]->index) {
			ee = et = e;
		}
		else {
			ee = Cudd_E(e);
			et = Cudd_T(e);
		}
		t = Cudd_T(dd);
		if (t->index > cvars[level]->index) {
			te = tt = t;
		}
		else {
			te = Cudd_E(t);
			tt = Cudd_T(t);
		}
	}

	export_rec(ee, rvars, cvars, num_vars, level+1, row->e, col->e, r, c);
	export_rec(et, rvars, cvars, num_vars, level+1, row->e, col->t, r, c+col->eoff);
	export_rec(te, rvars, cvars, num_vars, level+1, row->t, col->e, r+row->eoff, c);
	export_rec(tt, rvars, cvars, num_vars, level+1, row->t, col->t, r+row->eoff, c+col->eoff);
}

//------------------------------------------------------------------------------
