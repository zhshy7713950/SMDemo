/**
 * 
 */
package com.dazhihui.smdemo.network.packet;

/**
 * @author dzh
 *
 */

public interface IRequestListener {

	void handleResponse(IRequest request, IResponse response);
	
	void netException(IRequest request, Exception ex);
	
	void handleTimeout(IRequest request);
	
}
