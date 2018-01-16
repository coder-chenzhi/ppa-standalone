package edu.zju.ppa_standalone;

import java.io.File;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import ca.mcgill.cs.swevo.ppa.PPAOptions;
import ca.mcgill.cs.swevo.ppa.ui.PPAUtil;

public class PPAClient {

	// This must be called in an Eclipse plug-in, not in a
	// standalone Java application.
	public static void usePPA() {
		// The source file.
		File srcFile = new File(
				"E:/Code/EclipseCommitterWork/CodeMetrics/src/main/java/com/alibaba/analysis/metrics/Main.java");

		String logCode1 = "logger.error(\"bizMTOrderService.getOrderForPush orderId[\" + orderId + \"] false: [\"+ resultDO.getErrorDesc()+ \"]\");";
		String logCode2 = "LocExceptionLog.warn(LocMonitorLogUtil.getMonitorType(locBizOrderDO),ProcessEnum.Check,ModuleEnum.AbnormalOrder,locBizOrderDO.getBizOrderId(),null,locBizOrderDO.getOutTradeOrderId(),MonitorLogBaseUtil.buildExtLog(locBizOrderDO.getAbnormalType(),\"abnormal order\"));";
		
		// Obtaining a compilation unit using the all false options.
		// don't try to resolve any type
//		CompilationUnit cu = PPAUtil.getCUNoPPA(srcFile);
		
		ASTNode node = PPAUtil.getSnippet(logCode2, new PPAOptions(), false);
		
//		CompilationUnit cu = PPAUtil.getCUSkipResolve(partialCode, new PPAOptions());

		// Walk through the compilation unit.
		node.accept(new ASTVisitor() {
			public boolean visit(MethodInvocation node) {
				System.out.println(node);
				return false;
			}
			
			public boolean visit(Name node) {
				System.out.println(node);
				return false;
			}
		});

		// Obtain a name object and the corresponding binding
		// Name nameNode = ...
		// IBinding binding = nameNode.resolveBinding();
		// ITypeBinding typeBinding = nameNode.resolveTypeBinding();
	}

	public static void main(String[] args) {
		PPAClient.usePPA();
	}

}
