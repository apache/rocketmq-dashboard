// eslint-disable-next-line import/no-extraneous-dependencies
export default {
  'POST  /api/register': (_, res) => {
    res.send({
      data: {
        status: 'ok',
        currentAuthority: 'user',
      },
    });
  },
};
