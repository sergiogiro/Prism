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

#include <util.h>
#include <cudd.h>

//------------------------------------------------------------------------------

DdNode *DD_ThereExists(DdManager *ddman, DdNode *dd, DdNode **vars, int num_vars);
DdNode *DD_ForAll(DdManager *ddman, DdNode *dd, DdNode **vars, int num_vars);
DdNode *DD_SumAbstract(DdManager *ddman, DdNode *dd, DdNode **vars, int num_vars);
DdNode *DD_ProductAbstract(DdManager *ddman, DdNode *dd, DdNode **vars, int num_vars);
DdNode *DD_MinAbstract(DdManager *ddman, DdNode *dd, DdNode **vars, int num_vars);
DdNode *DD_MaxAbstract(DdManager *ddman, DdNode *dd, DdNode **vars, int num_vars);

//------------------------------------------------------------------------------
