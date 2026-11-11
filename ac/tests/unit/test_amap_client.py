"""高德客户端 POI 字段测试。"""


async def test_search_poi_includes_external_id(load_module):
    module = load_module("amap_client_under_test", "agent/tripAgent/AmapClient.py")
    client = module.AmapClient()

    async def fake_get(path, params):
        return {
            "pois": [
                {
                    "id": "B0FFH123",
                    "name": "西湖",
                    "location": "120.155070,30.274084",
                    "address": "西湖区",
                    "type": "风景名胜;公园",
                    "typecode": "110000",
                    "cityname": "杭州市",
                }
            ]
        }

    client._get = fake_get

    pois = await client.search_poi("西湖", "杭州")

    assert pois == [{
        "id": "B0FFH123",
        "name": "西湖",
        "coords": [120.15507, 30.274084],
        "location": "120.155070,30.274084",
        "address": "西湖区",
        "type": "风景名胜;公园",
        "typecode": "110000",
        "city": "杭州市",
    }]
