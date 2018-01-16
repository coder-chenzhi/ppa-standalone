package zzzsnippet;
public class ZZZSnippet extends ZZZSuperZZZSnippet {
  public void mainZZZSnippet() {
LocExceptionLog.warn(LocMonitorLogUtil.getMonitorType(locBizOrderDO),ProcessEnum.Check,ModuleEnum.AbnormalOrder,locBizOrderDO.getBizOrderId(),null,locBizOrderDO.getOutTradeOrderId(),MonitorLogBaseUtil.buildExtLog(locBizOrderDO.getAbnormalType(),"abnormal order"));
  }

}
