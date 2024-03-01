// SPDX-License-Identifier: Apache-2.0
/*
 *   v1.0.0
 *   hub contract for WeCross
 *   main entrance of interchain call
 */

pragma solidity >=0.5.0 <0.8.20;
pragma experimental ABIEncoderV2;

contract WeCrossHub {
    // string constant EVENT_TYPE = "INTERCHAIN";

    uint256 increment;     // 默认初始化为0
 
    uint256 currentIndex;  // 默认初始化为0

    string constant NULL_FLAG = "null";

    string constant VERSION = "v1.0.0";

    string constant CALL_TYPE_QUERY = "0";

    string constant CALL_TYPE_INVOKE = "1";

    mapping(uint256 => string) requests;

    mapping(string => string[]) callbackResults;

    function getVersion() public pure
    returns (string memory) {
        return VERSION;
    }

    // get current uid
    function getIncrement() public view
    returns (uint256) {
        return increment;
    }

    // invoke other chain
    function interchainInvoke(string memory _path, string memory _method, string[] memory _args, string memory _callbackPath, string memory _callbackMethod) public
    returns (string memory uid) {
        return handleRequest(CALL_TYPE_INVOKE, _path, _method, _args, _callbackPath, _callbackMethod);
    }

    // query other chain, not support right now
    function interchainQuery(string memory _path, string memory _method, string[] memory _args, string memory _callbackPath, string memory _callbackMethod) public
    returns (string memory uid) {
        return handleRequest(CALL_TYPE_QUERY, _path, _method, _args, _callbackPath, _callbackMethod);
    }

    function handleRequest(string memory _callType, string memory _path, string memory _method, string[] memory _args, string memory _callbackPath, string memory _callbackMethod) private
    returns (string memory uid) {
        uid = uint256ToString(++increment);

        string[] memory reuqest = new string[](8);
        reuqest[0] = uid;
        reuqest[1] = _callType;
        reuqest[2] = _path;
        reuqest[3] = _method;
        reuqest[4] = serializeStringArray(_args);
        reuqest[5] = _callbackPath;
        reuqest[6] = _callbackMethod;
        reuqest[7] = addressToString(tx.origin);

        requests[increment] = serializeStringArray(reuqest);
    }

    function getInterchainRequests(uint256 _num) public view
    returns (string memory) {
        
        if (currentIndex == increment) {
            return NULL_FLAG;
        }
        // uint256 num = _num < (increment - currentIndex) ? _num : (increment - currentIndex);
        uint256 m = increment - currentIndex;
       
        if(_num < m){
           m = _num;
        }

        string[] memory tempRequests = new string[](m);
        for (uint256 i = 0; i < m; i++) {
            tempRequests[i] = requests[currentIndex + i + 1];
        }

        return serializeStringArray(tempRequests);
    }

    function updateCurrentRequestIndex(uint256 _index) public {
        if (currentIndex < _index) {
            currentIndex = _index;
        }
    }

    // _result is json form of arrays
    function registerCallbackResult(string memory _uid, string memory _tid, string memory _seq, string memory _errorCode, string memory _errorMsg, string[] memory _result) public {
        string[5] memory result = [_tid, _seq, _errorCode, _errorMsg, serializeStringArray(_result)];
        callbackResults[_uid] = result;
    }

    function selectCallbackResult(string memory _uid) public view
    returns (string[] memory) {
        return callbackResults[_uid];
    }

    function serializeStringArray(string[] memory _arr) internal pure
    returns (string memory jsonStr) {
        // uint256 len = _arr.length;
        if (_arr.length == 0) {
            return "[]";
        }

        jsonStr = "[";
        for (uint256 i = 0; i < _arr.length - 1; i++) {
            jsonStr = string(abi.encodePacked(jsonStr, '"'));
            jsonStr = string(abi.encodePacked(jsonStr, jsonEscape(_arr[i])));
            jsonStr = string(abi.encodePacked(jsonStr, '",'));
        }

        jsonStr = string(abi.encodePacked(jsonStr, '"'));
        jsonStr = string(abi.encodePacked(jsonStr, jsonEscape(_arr[_arr.length - 1])));
        jsonStr = string(abi.encodePacked(jsonStr, '"'));
        jsonStr = string(abi.encodePacked(jsonStr, "]"));
    }

    

    function jsonEscape(string memory _str) internal pure
    returns (string memory) {
        bytes memory bts = bytes(_str);
        uint256 len = bts.length;
        uint256 j = 0;
        for (; j < len; j++) {
            if (bts[j] == "\\" || bts[j] == '"') {
                bts[j] = "\\";
            }
        }
        return string(bts);
    }

    function addressToString(address addr) internal pure returns(string memory){
        //Convert addr to bytes
        bytes20 value = bytes20(uint160(addr));
        bytes memory strBytes = new bytes(42);
        strBytes[0] = '0';
        strBytes[1] = 'x';

        for(uint i = 0; i < 20; i++){
            uint8 byteValue = uint8(value[i]);
            strBytes[2 + (i<<1)] = encode((byteValue >> 4) & 0x0f);
            strBytes[3 + (i<<1)] = encode(byteValue & 0x0f);
        }
        return string(strBytes);
    }

    function encode(uint8 num) internal pure returns(bytes1){
        //0-9 -> 0-9
        if(num >= 0 && num <= 9){
            return bytes1(num + 48);
        }
        //10-15 -> a-f
        return bytes1(num + 87);
    }

    function uint256ToString(uint256 _value) internal pure returns (string memory) {
        bytes memory reversed = new bytes(32);
        uint i = 0; 
        while (_value != 0) { 
            uint8 remainder = uint8(_value % 10); 
            _value = _value / 10; 
            reversed[i % 32] = bytes1(48 + remainder); 
            i++;
        } 
        // bytes memory s = new bytes(i + 1); 
        bytes memory s = new bytes(32); 
        for (uint j = 1; j <= i % 32; j++) { 
            s[j-1] = reversed[i - j];
        } 
        return string(s);
    }
    
}
