package ca.mcgill.cs.swevo.ppa.inference;

import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractInferenceStrategy;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.PPABindingsUtil;
import org.eclipse.jdt.core.dom.PPAEngine;

import ca.mcgill.cs.swevo.ppa.PPAIndex;
import ca.mcgill.cs.swevo.ppa.PPAIndexer;
import ca.mcgill.cs.swevo.ppa.TypeFact;

public class ConstructorThisInferenceStrategy extends AbstractInferenceStrategy {

	public ConstructorThisInferenceStrategy(PPAIndexer indexer, PPAEngine ppaEngine) {
		super(indexer, ppaEngine);
	}

	public void inferTypes(ASTNode node) {
	}
	
	public boolean isSafe(ASTNode node) {
		boolean isSafe = false;

		if (!isSafe) {
			ConstructorInvocation ci = (ConstructorInvocation) node;
			IMethodBinding mBinding = (IMethodBinding) ci.resolveConstructorBinding();
			// Should not happen
			if (mBinding == null) {
				return false;
			}
			
			ITypeBinding[] paramTypes = mBinding.getParameterTypes();
			isSafe = true;
			for (ITypeBinding paramType : paramTypes) {
				isSafe = isSafe && PPABindingsUtil.getSafetyValue(paramType) > PPABindingsUtil.UNKNOWN_TYPE;
			}
		}

		return isSafe;
	}
	
	public void makeSafe(ASTNode node, TypeFact typeFact) {
	}

	public void makeSafeSecondary(ASTNode node, TypeFact typeFact) {
		ConstructorInvocation ci = (ConstructorInvocation) node;
		PPABindingsUtil.fixThisConstructor(ci, ppaEngine.getRegistry(),
				getResolver(node), indexer, ppaEngine, !ppaEngine.isInMethodBindingPass(),
				ppaEngine.isInMethodBindingPass());
	}
	
	@Override
	public List<PPAIndex> getSecondaryIndexes(ASTNode node) {
		List<PPAIndex> indexes = super.getSecondaryIndexes(node);
		ConstructorInvocation ci = (ConstructorInvocation) node;

		for (Object arg : ci.arguments()) {
			ASTNode argNode = (ASTNode) arg;
			if (indexer.isIndexable(argNode)) {
				indexes.add(indexer.getMainIndex(argNode));
			}
		}

		return indexes;
	}
	
}
