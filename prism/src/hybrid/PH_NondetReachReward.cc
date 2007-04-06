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

// local prototypes
static void mult_rec(HDDNode *hdd, int level, int row_offset, int col_offset, int code);
static void mult_rm(RMSparseMatrix *rmsm, int row_offset, int col_offset, int code);
static void mult_cmsr(CMSRSparseMatrix *cmsrsm, int row_offset, int col_offset, int code);

// globals (used by local functions)
static HDDNode *zero;
static int num_levels;
static bool compact_sm;
static double *sm_dist;
static int sm_dist_shift;
static int sm_dist_mask;
static double *soln, *soln2, *soln3;

//------------------------------------------------------------------------------

JNIEXPORT jint JNICALL Java_hybrid_PrismHybrid_PH_1NondetReachReward
(
JNIEnv *env,
jclass cls,
jint t,			// trans matrix
jint sr,		// state rewards
jint trr,		// transition rewards
jint od,		// odd
jint rv,		// row vars
jint num_rvars,
jint cv,		// col vars
jint num_cvars,
jint ndv,		// nondet vars
jint num_ndvars,
jint g,			// 'goal' states
jint in,		// 'inf' states
jint m,		// 'maybe' states
jboolean min	// min or max probabilities (true = min, false = max)
)
{
	// cast function parameters
	DdNode *trans = (DdNode *)t;		// trans matrix
	DdNode *state_rewards = (DdNode *)sr;	// state rewards
	DdNode *trans_rewards = (DdNode *)trr;	// transition rewards
	ODDNode *odd = (ODDNode *)od; 		// reachable states
	DdNode **rvars = (DdNode **)rv; 	// row vars
	DdNode **cvars = (DdNode **)cv; 	// col vars
	DdNode **ndvars = (DdNode **)ndv;	// nondet vars
	DdNode *goal = (DdNode *)g;	// 'goal' states
	DdNode *inf = (DdNode *)in; 	// 'inf' states
	DdNode *maybe = (DdNode *)m; 	// 'inf' states
	// mtbdds
	DdNode *reach, *a;
	// model stats
	int n, nm;
	// flags
	bool compact_r;
	// hybrid stuff	
	HDDMatrices *hddms, *hddms2;
	HDDMatrix *hddm;
	HDDNode *hdd;
	// vectors
	double *rew_vec, *tmpsoln;
	DistVector *rew_dist;
	// timing stuff
	long start1, start2, start3, stop;
	double time_taken, time_for_setup, time_for_iters;
	// misc
	int i, j, k, iters;
	double d, kb, kbt;
	bool done;
	
	// start clocks
	start1 = start2 = util_cpu_time();
	
	// get number of states
	n = odd->eoff + odd->toff;
	
	// get reachable states
	reach = odd->dd;
	
	// filter out rows (goal states and infinity states) from matrix
	Cudd_Ref(trans);
	Cudd_Ref(maybe);
	a = DD_Apply(ddman, APPLY_TIMES, trans, maybe);
	
	// build hdds for matrix
	PH_PrintToMainLog(env, "\nBuilding hybrid MTBDD matrices... ");
	hddms = build_hdd_matrices_mdp(a, NULL, rvars, cvars, num_rvars, ndvars, num_ndvars, odd);
	nm = hddms->nm;
	kb = hddms->mem_nodes;
	kbt = kb;
	PH_PrintToMainLog(env, "[nm=%d, levels=%d, nodes=%d] [%.1f KB]\n", hddms->nm, hddms->num_levels, hddms->num_nodes, kb);
	
	// add sparse bits
	PH_PrintToMainLog(env, "Adding sparse bits... ");
	add_sparse_matrices_mdp(hddms, compact);
	kb = hddms->mem_sm;
	kbt += kb;
	PH_PrintToMainLog(env, "[levels=%d-%d, num=%d, compact=%d/%d] [%.1f KB]\n", hddms->l_sm_min, hddms->l_sm_max, hddms->num_sm, hddms->compact_sm, hddms->nm, kb);
	
	// multiply transition rewards by transition probs and sum rows
	// (note also filters out unwanted states at the same time)
	Cudd_Ref(trans_rewards);
	Cudd_Ref(a);
	trans_rewards = DD_Apply(ddman, APPLY_TIMES, trans_rewards, a);
	trans_rewards = DD_SumAbstract(ddman, trans_rewards, cvars, num_cvars);
	trans_rewards = DD_Apply(ddman, APPLY_TIMES, trans_rewards, DD_SetVectorElement(ddman, DD_Constant(ddman, 0), cvars, num_cvars, 0, 1));
	
	// build hdds for transition rewards matrix
	PH_PrintToMainLog(env, "Building hybrid MTBDD matrices for rewards... ");
	hddms2 = build_hdd_matrices_mdp(trans_rewards, hddms, rvars, cvars, num_rvars, ndvars, num_ndvars, odd);
	kb = hddms2->mem_nodes;
	kbt = kb;
	PH_PrintToMainLog(env, "[nm=%d, levels=%d, nodes=%d] [%.1f KB]\n", hddms2->nm, hddms2->num_levels, hddms2->num_nodes, kb);
	
	// add sparse bits
	PH_PrintToMainLog(env, "Adding sparse bits... ");
	add_sparse_matrices_mdp(hddms2, compact);
	kb = hddms2->mem_sm;
	kbt += kb;
	PH_PrintToMainLog(env, "[levels=%d-%d, num=%d, compact=%d/%d] [%.1f KB]\n", hddms2->l_sm_min, hddms2->l_sm_max, hddms2->num_sm, hddms2->compact_sm, hddms2->nm, kb);
	
	// remove goal and infinity states from state rewards vector
	Cudd_Ref(state_rewards);
	Cudd_Ref(maybe);
	state_rewards = DD_Apply(ddman, APPLY_TIMES, state_rewards, maybe);
	
	// put state rewards in a vector
	PH_PrintToMainLog(env, "Creating rewards vector... ");
	rew_vec = mtbdd_to_double_vector(ddman, state_rewards, rvars, num_rvars, odd);
	// try and convert to compact form if required
	compact_r = false;
	if (compact) {
		if (rew_dist = double_vector_to_dist(rew_vec, n)) {
			compact_r = true;
			free(rew_vec);
		}
	}
	kb = (!compact_r) ? n*8.0/1024.0 : (rew_dist->num_dist*8.0+n*2.0)/1024.0;
	kbt += kb;
	if (!compact_r) PH_PrintToMainLog(env, "[%.1f KB]\n", kb);
	else PH_PrintToMainLog(env, "[dist=%d, compact] [%.1f KB]\n", rew_dist->num_dist, kb);
	
	// create solution/iteration vectors
	PH_PrintToMainLog(env, "Allocating iteration vectors... ");
	soln = new double[n];
	soln2 = new double[n];
	soln3 = new double[n];
	kb = n*8.0/1024.0;
	kbt += 3*kb;
	PH_PrintToMainLog(env, "[3 x %.1f KB]\n", kb);
	
	// print total memory usage
	PH_PrintToMainLog(env, "TOTAL: [%.1f KB]\n", kbt);
	
	// initial solution is zero
	for (i = 0; i < n; i++) {
		soln[i] = 0;
	}
	
	// get setup time
	stop = util_cpu_time();
	time_for_setup = (double)(stop - start2)/1000;
	start2 = stop;
	
	// start iterations
	iters = 0;
	done = false;
	PH_PrintToMainLog(env, "\nStarting iterations...\n");
	
	while (!done && iters < max_iters) {
		
		iters++;
		
//		PH_PrintToMainLog(env, "iter %d\n", iters);
//		start3 = util_cpu_time();
		
		// initialise array for storing mins/maxs to -1s
		// (allows us to keep track of rows not visited)
		for (i = 0; i < n; i++) {	
			soln2[i] = -1;
		}
		
		// do matrix multiplication and min/max
		for (i = 0; i < nm; i++) {
			
			// start off all negative
			// (need to keep track of rows not visited)
			for (j = 0; j < n; j++) {
				soln3[j] = -1;
			}
			
			// matrix multiply
			// store stuff to be used globally
			hddm = hddms->choices[i];
			hdd = hddm->top;
			zero = hddm->zero;
			num_levels = hddm->num_levels;
			compact_sm = hddm->compact_sm;
			if (compact_sm) {
				sm_dist = hddm->dist;
				sm_dist_shift = hddm->dist_shift;
				sm_dist_mask = hddm->dist_mask;
			}
			// do traversal
			mult_rec(hdd, 0, 0, 0, 1);
			
			// add transition rewards
			// store stuff to be used globally
			hddm = hddms2->choices[i];
			hdd = hddm->top;
			zero = hddm->zero;
			num_levels = hddm->num_levels;
			compact_sm = hddm->compact_sm;
			if (compact_sm) {
				sm_dist = hddm->dist;
				sm_dist_shift = hddm->dist_shift;
				sm_dist_mask = hddm->dist_mask;
			}
			// do traversal
			mult_rec(hdd, 0, 0, 0, 2);
			
			// min/max
			for (j = 0; j < n; j++) {
				if (soln3[j] >= 0) {
					if (soln2[j] < 0) {
						soln2[j] = soln3[j];
					} else if (min) {
						if (soln3[j] < soln2[j]) soln2[j] = soln3[j];
					} else {
						if (soln3[j] > soln2[j]) soln2[j] = soln3[j];
					}
				}
			}
		}
		
		// add state rewards
		if (!compact_r) {
			for (i = 0; i < n; i++) { if(soln2[i] < 0) soln2[i] = 0; soln2[i] += rew_vec[i]; }
		} else {
			for (i = 0; i < n; i++) { if(soln2[i] < 0) soln2[i] = 0; soln2[i] += rew_dist->dist[rew_dist->ptrs[i]]; }
		}
		
		// check convergence
		switch (term_crit) {
		case TERM_CRIT_ABSOLUTE:
			done = true;
			for (i = 0; i < n; i++) {
				if (fabs(soln2[i] - soln[i]) > term_crit_param) {
					done = false;
					break;
				}
				
			}
			break;
		case TERM_CRIT_RELATIVE:
			done = true;
			for (i = 0; i < n; i++) {
				if (fabs(soln2[i] - soln[i])/soln2[i] > term_crit_param) {
					done = false;
					break;
				}
				
			}
			break;
		}
		
		// prepare for next iteration
		tmpsoln = soln;
		soln = soln2;
		soln2 = tmpsoln;
		
//		PH_PrintToMainLog(env, "%.2f %.2f sec\n", ((double)(util_cpu_time() - start3)/1000), ((double)(util_cpu_time() - start2)/1000)/iters);
	}
	
	// stop clocks
	stop = util_cpu_time();
	time_for_iters = (double)(stop - start2)/1000;
	time_taken = (double)(stop - start1)/1000;
	
	// print iterations/timing info
	PH_PrintToMainLog(env, "\nIterative method: %d iterations in %.2f seconds (average %.6f, setup %.2f)\n", iters, time_taken, time_for_iters/iters, time_for_setup);
	
	// free memory
	Cudd_RecursiveDeref(ddman, a);
	Cudd_RecursiveDeref(ddman, state_rewards);
	Cudd_RecursiveDeref(ddman, trans_rewards);
	free_hdd_matrices_mdp(hddms);
	free_hdd_matrices_mdp(hddms2);
	if (compact_r) free_dist_vector(rew_dist); else free(rew_vec);
	delete soln2;
	delete soln3;
	
	// if the iterative method didn't terminate, this is an error
	if (!done) { delete soln; PH_SetErrorMessage("Iterative method did not converge within %d iterations.\nConsider using a different numerical method or increasing the maximum number of iterations", iters); return 0; }
	
	return (int)soln;
}

//------------------------------------------------------------------------------

void mult_rec(HDDNode *hdd, int level, int row_offset, int col_offset, int code)
{
	HDDNode *e, *t;
	
	// if it's the zero node
	if (hdd == zero) {
		return;
	}
	// or if we've reached a submatrix
	// (check for non-null ptr but, equivalently, we could just check if level==l_sm)
	else if (hdd->sm) {
		if (!compact_sm) {
			mult_rm((RMSparseMatrix *)hdd->sm, row_offset, col_offset, code);
		} else {
			mult_cmsr((CMSRSparseMatrix *)hdd->sm, row_offset, col_offset, code);
		}
		return;
	}
	// or if we've reached the bottom
	else if (level == num_levels) {
		//printf("(%d,%d)=%f\n", row_offset, col_offset, hdd->type.val);
		switch (code) {
		case 1:
			if (soln3[row_offset] < 0) soln3[row_offset] = 0;
			soln3[row_offset] += soln[col_offset] * hdd->type.val;
			break;
		case 2:
			if (soln3[row_offset] < 0) soln3[row_offset] = 0;
			soln3[row_offset] += hdd->type.val;
			break;
		}
		return;
	}
	// otherwise recurse
	e = hdd->type.kids.e;
	if (e != zero) {
		mult_rec(e->type.kids.e, level+1, row_offset, col_offset, code);
		mult_rec(e->type.kids.t, level+1, row_offset, col_offset+e->off, code);
	}
	t = hdd->type.kids.t;
	if (t != zero) {
		mult_rec(t->type.kids.e, level+1, row_offset+hdd->off, col_offset, code);
		mult_rec(t->type.kids.t, level+1, row_offset+hdd->off, col_offset+t->off, code);
	}
}

//-----------------------------------------------------------------------------------

void mult_rm(RMSparseMatrix *rmsm, int row_offset, int col_offset, int code)
{
	int i2, j2, l2, h2, r;
	int sm_n = rmsm->n;
	int sm_nnz = rmsm->nnz;
	double *sm_non_zeros = rmsm->non_zeros;
	unsigned char *sm_row_counts = rmsm->row_counts;
	int *sm_row_starts = (int *)rmsm->row_counts;
	bool sm_use_counts = rmsm->use_counts;
	unsigned int *sm_cols = rmsm->cols;
	
	// loop through rows of submatrix
	l2 = sm_nnz; h2 = 0;
	for (i2 = 0; i2 < sm_n; i2++) {
		
		// loop through entries in this row
		if (!sm_use_counts) { l2 = sm_row_starts[i2]; h2 = sm_row_starts[i2+1]; }
		else { l2 = h2; h2 += sm_row_counts[i2]; }
		for (j2 = l2; j2 < h2; j2++) {
			switch (code) {
			case 1:
				r = row_offset + i2;
				if (soln3[r] < 0) soln3[r] = 0;
				soln3[r] += soln[col_offset + sm_cols[j2]] * sm_non_zeros[j2];
				break;
			case 2:
				r = row_offset + i2;
				if (soln3[r] < 0) soln3[r] = 0;
				soln3[r] += sm_non_zeros[j2];
				break;
			}
			//printf("(%d,%d)=%f\n", row_offset + i2, col_offset + sm_cols[j2], sm_non_zeros[j2]);
		}
	}
}

//-----------------------------------------------------------------------------------

void mult_cmsr(CMSRSparseMatrix *cmsrsm, int row_offset, int col_offset, int code)
{
	int i2, j2, l2, h2, r;
	int sm_n = cmsrsm->n;
	int sm_nnz = cmsrsm->nnz;
	unsigned char *sm_row_counts = cmsrsm->row_counts;
	int *sm_row_starts = (int *)cmsrsm->row_counts;
	bool sm_use_counts = cmsrsm->use_counts;
	unsigned int *sm_cols = cmsrsm->cols;
	
	// loop through rows of submatrix
	l2 = sm_nnz; h2 = 0;
	for (i2 = 0; i2 < sm_n; i2++) {
		
		// loop through entries in this row
		if (!sm_use_counts) { l2 = sm_row_starts[i2]; h2 = sm_row_starts[i2+1]; }
		else { l2 = h2; h2 += sm_row_counts[i2]; }
		for (j2 = l2; j2 < h2; j2++) {
			switch (code) {
			case 1:
				r = row_offset + i2;
				if (soln3[r] < 0) soln3[r] = 0;
				soln3[r] += soln[col_offset + (int)(sm_cols[j2] >> sm_dist_shift)] * sm_dist[(int)(sm_cols[j2] & sm_dist_mask)];
				break;
			case 2:
				r = row_offset + i2;
				if (soln3[r] < 0) soln3[r] = 0;
				soln3[r] += sm_dist[(int)(sm_cols[j2] & sm_dist_mask)];
				break;
			}
			//`("(%d,%d)=%f\n", row_offset + i2, col_offset + (int)(sm_cols[j2] >> sm_dist_shift), sm_dist[(int)(sm_cols[j2] & sm_dist_mask)]);
		}
	}
}

//------------------------------------------------------------------------------
